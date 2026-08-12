package com.yang.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 *对话历史存磁盘（.kryo），每次请求时取最近 N 条拼进 Prompt 发给 LLM，重启不丢。
 * <p>两层存储：
 * <ul>
 *   <li>ConcurrentHashMap —— 内存缓存，纳秒级读取，重启就没了</li>
 *   <li>.kryo 文件 —— 磁盘持久化，重启还在，缓存没命中时才读</li>
 * </ul>
 *
 * <p>数据流：add() 追加 → .kryo 存全量 → get() 截取最近 N 条给 LLM，旧消息只在文件里，不占 Token。
 */
public class FileBasedChatMemory implements ChatMemory {

    /** .kryo 文件存放目录 */
    private final String BASE_DIR;

    /** 内存缓存：conversationId → 全量历史消息，多用户并发安全 */
    private final ConcurrentHashMap<String, List<Message>> cache = new ConcurrentHashMap<>();

    /** 每次丢给 LLM 的历史消息条数，在 yml 里配置 */
    private final int maxRecentMessages;

    /** 全局唯一的序列化引擎，把 Java 对象转成字节写入 .kryo，或反过来读 */
    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public FileBasedChatMemory(String dir, int maxRecentMessages) {
        this.BASE_DIR = dir;
        this.maxRecentMessages = maxRecentMessages;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    // ==================== ChatMemory 接口实现 ====================

    /** 追加本轮新消息到全量历史 → 写缓存 + 写磁盘 */
    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> all = getFull(conversationId);  // 拿全量，不能截断
        all.addAll(messages);
        cache.put(conversationId, all);
        saveConversation(conversationId, all);
        System.out.printf("【用户聊天记忆 缓存历史上下文消息】，用户id=%s | 新增=%d条 | 历史消息=%d条%n",
                conversationId, messages.size(), all.size());
    }

    /** 读历史，但只返回最近 N 条给 LLM，省 Token */
    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = getFull(conversationId);
        if (all.size() > maxRecentMessages) {
            List<Message> recent = all.subList(all.size() - maxRecentMessages, all.size());
            System.out.printf("【用户聊天记忆 获取历史上下文消息】 ，用户id=%s | 全部数据=%d条 → 取最近%d条%n",
                    conversationId, all.size(), maxRecentMessages);
            return recent;
        }
        System.out.printf("【用户聊天记忆 获取历史上下文消息】，用户id=%s | 全部数据=%d条 → 未超阈值，全量给LLM%n",
                conversationId, all.size());
        return all;
    }

    /** 清空某个用户的记忆：删缓存 + 删 .kryo 文件 */
    @Override
    public void clear(String conversationId) {
        cache.remove(conversationId);
        File file = getConversationFile(conversationId);
        if (file.exists()) file.delete();
    }

    /** 清空所有用户的记忆，返回删除的文件数 */
    public int clearAll() {
        int count = 0;
        cache.clear();
        File dir = new File(BASE_DIR);
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                if (f.getName().endsWith(".kryo") || f.getName().endsWith(".tmp")) {
                    if (f.delete()) count++;
                }
            }
        }
        return count;
    }

    // ==================== 内部实现 ====================

    /** 先查缓存 → 缓存没中就读磁盘 → 放缓存后返回 */
    private List<Message> getFull(String conversationId) {
        List<Message> cached = cache.get(conversationId);
        if (cached != null) return cached;

        List<Message> messages = getOrCreateConversation(conversationId);
        cache.put(conversationId, messages);
        System.out.printf("[ChatMemory] 首次加载 会话=%s | 全量=%d条%n", conversationId, messages.size());
        return messages;
    }

    /** 从 .kryo 反序列化，文件不存在就返回空列表，文件损坏就删掉重新开始 */
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) return new ArrayList<>();

        try (Input input = new Input(new FileInputStream(file))) {
            return kryo.readObject(input, ArrayList.class);
        } catch (Exception e) {
            System.err.println("对话记忆文件损坏，已删除: " + file.getName());
            file.delete();
            return new ArrayList<>();
        }
    }

    /** 序列化写入 .kryo：先写 .tmp → 删旧文件 → .tmp 改名，防止写一半断电导致文件损坏 */
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        File tempFile = new File(BASE_DIR, conversationId + ".tmp");

        try (Output output = new Output(new FileOutputStream(tempFile))) {
            kryo.writeObject(output, messages);
        } catch (Exception e) {
            e.printStackTrace();
            tempFile.delete();
            return;
        }

        file.delete();
        tempFile.renameTo(file);
    }

    /** conversationId → 对应的 .kryo 文件，每个用户一个文件，互不干扰 */
    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}
