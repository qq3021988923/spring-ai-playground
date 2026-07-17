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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于文件持久化的对话记忆
 */
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;

    // 高性能 java 序例化库，比如 java 原生
    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        // 设置实例化策略
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    // 构造对象时，指定文件保存目录 传进来，不存在就自动建目录。
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }

    @Override
    public List<Message> get(String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                messages = kryo.readObject(input, ArrayList.class);
            } catch (Exception e) {
                // 文件损坏（如服务中断时写入不完整），删掉坏文件，重新开始
                System.err.println("对话记忆文件损坏，已删除并重新创建: " + file.getName());
                file.delete();
            }
        }
        return messages;
    }

    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        // 先写临时文件，写完再改名，防止写入中断导致文件损坏
        File tempFile = new File(BASE_DIR, conversationId + ".tmp");
        try (Output output = new Output(new FileOutputStream(tempFile))) {
            kryo.writeObject(output, messages);
        } catch (Exception e) {
            e.printStackTrace();
            tempFile.delete();
            return;
        }
        // 原子替换：临时文件 → 正式文件
        file.delete();
        tempFile.renameTo(file);
    }

    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}
