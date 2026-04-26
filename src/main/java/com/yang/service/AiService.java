package com.yang.service;

import com.yang.model.LoveAdvice;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;

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

}