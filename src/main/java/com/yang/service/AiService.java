package com.yang.service;

import com.yang.model.LoveAdvice;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 服务类
 * 学习第2步：系统提示词 + 结构化输出
 */
@Slf4j
@Service
public class AiService {

    @Resource
    private ChatModel chatModel;

    /**
     * 示例1：使用系统提示词，让 AI 扮演特定角色
     * 比如：让 AI 扮演一个恋爱顾问
     */
    public String chatAsLoveAdvisor(String userMessage) {
        // 系统提示词：设定 AI 的角色和行为
        String systemPrompt = """
            你是一位专业的恋爱顾问，名叫"小红娘"。
            你的特点是：
            1. 温柔体贴，善解人意
            2. 善于倾听，给出实用的建议
            3. 语言亲切，使用 emoji 让对话更生动
            4. 如果用户遇到感情问题，先安抚，再给出建议
            
            请用这个身份与用户对话。
            """;

        // 构建消息列表：系统消息 + 用户消息
        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        );
        System.out.println("构建消息列表=="+messages);

        ChatResponse response = chatModel.call(new Prompt(messages));
        String answer = response.getResult().getOutput().getText();
        log.info("恋爱顾问回复：{}", answer);
        return answer;
    }

    /**
     * 示例2：结构化输出 - 将 AI 的回复转换为 Java 对象
     * 比如：让 AI 生成一个恋爱建议对象
     */
    public LoveAdvice getLoveAdvice(String situation) {
        // 创建输出转换器，指定要转换的目标类
        BeanOutputConverter<LoveAdvice> converter = new BeanOutputConverter<>(LoveAdvice.class);
        System.out.println("66666"+converter);
        // 用户提示词，告诉 AI 我们需要什么格式
        String userPrompt = """
            请根据以下情况，给出恋爱建议：
            %s
            
            请按照以下格式输出 JSON：
            %s
            """.formatted(situation, converter.getFormat());

        // 调用 AI
        String jsonResponse = chatModel.call(userPrompt);
        log.info("AI 返回的 JSON：{}", jsonResponse);

        // 将 JSON 转换为 Java 对象
            LoveAdvice loveAdvice = converter.convert(jsonResponse);
        return loveAdvice;
    }


    // ====== 会话记忆：存储每个用户的对话历史 ======
    // 注意：实际项目中应该用 Redis 或数据库存储，这里为了演示用 Map
    private final Map<String, List<Message>> chatMemory = new ConcurrentHashMap<>();


    /**
     * 示例3：使用 Prompt Template（提示词模板） 比上面多个这个 PromptTemplate
     * 比如：写一封邮件
     * recipient：接收人
     * tone：语气
     * content：内容
     */
    public String writeEmail(String recipient, String tone, String content) {
        // 定义提示词模板，使用 {变量名} 占位
        String promptTemplate = """
            请帮我写一封邮件。
            
            收件人：{recipient}
            语气：{tone}
            核心内容：{content}
            
            请直接写出邮件正文，不需要其他说明。
            """;

        // 创建 PromptTemplate 对象
        PromptTemplate template = new PromptTemplate(promptTemplate);

        // 填充变量
        Prompt prompt = template.create(Map.of(
                "recipient", recipient,
                "tone", tone,
                "content", content
        ));

        // 调用 AI
        ChatResponse response = chatModel.call(prompt);
        String email = response.getResult().getOutput().getText();
        log.info("生成的邮件：{}", email);
        return email;
    }

    /**
     * 示例4：带记忆的对话（Chat Memory）
     * 每个 userId 对应一个独立的对话历史
     */
    public String chatWithMemory(String userId, String userMessage) {
        // 获取或初始化该用户的对话历史
        List<Message> messages = chatMemory.computeIfAbsent(userId, k -> {
            List<Message> init = new ArrayList<>();
            // 添加系统提示词
            init.add(new SystemMessage("你是一个 helpful 的 AI 助手。"));
            return init;
        });

        // 添加当前用户消息
        messages.add(new UserMessage(userMessage));

        // 调用 AI
        ChatResponse response = chatModel.call(new Prompt(messages));
        String answer = response.getResult().getOutput().getText();

        // 保存 AI 回复到历史记录
        messages.add(new AssistantMessage(answer));
        log.info("对话历史长度：{}", messages);
        log.info("用户 {} 的对话历史长度：{}", userId, messages.size());
        return answer;
    }

    /**
     * 清空某个用户的对话记忆
     */
    public void clearMemory(String userId) {
        chatMemory.remove(userId);
        log.info("已清空用户 {} 的对话记忆", userId);
    }




}