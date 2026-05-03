package com.yang.service;

import com.yang.model.LoveAdvice;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiService {

    @Resource(name = "dashscopeChatModel")
    private ChatModel chatModel;

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String chatAsLoveAdvisor(String userMessage) {
        String systemPrompt = """
            你是一位专业的恋爱顾问，名叫"小红娘"。
            你的特点是：
            1. 温柔体贴，善解人意
            2. 善于倾听，给出实用的建议
            3. 语言亲切，使用 emoji 让对话更生动
            4. 如果用户遇到感情问题，先安抚，再给出建议

            请用这个身份与用户对话。
            """;

        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        );

        ChatResponse response = chatModel.call(new Prompt(messages));
        String answer = response.getResult().getOutput().getText();
        log.info("恋爱顾问回复：{}", answer);
        return answer;
    }

    public LoveAdvice getLoveAdvice(String situation) {
        BeanOutputConverter<LoveAdvice> converter = new BeanOutputConverter<>(LoveAdvice.class);
        String userPrompt = """
            请根据以下情况，给出恋爱建议：
            %s
            
            请按照以下格式输出 JSON：
            %s
            """.formatted(situation, converter.getFormat());

        String jsonResponse = chatModel.call(userPrompt);
        log.info("AI 返回的 JSON：{}", jsonResponse);

        LoveAdvice loveAdvice = converter.convert(jsonResponse);
        return loveAdvice;
    }

    private final Map<String, List<Message>> chatMemory = new ConcurrentHashMap<>();

    public String writeEmail(String recipient, String tone, String content) {
        String promptTemplate = """
            请帮我写一封邮件。
            
            收件人：{recipient}
            语气：{tone}
            核心内容：{content}
            
            请直接写出邮件正文，不需要其他说明。
            """;

        PromptTemplate template = new PromptTemplate(promptTemplate);
        Prompt prompt = template.create(Map.of(
                "recipient", recipient,
                "tone", tone,
                "content", content
        ));

        ChatResponse response = chatModel.call(prompt);
        String email = response.getResult().getOutput().getText();
        log.info("生成的邮件：{}", email);
        return email;
    }

    public String chatWithMemory(String userId, String userMessage) {
        List<Message> messages = chatMemory.computeIfAbsent(userId, k -> {
            List<Message> init = new ArrayList<>();
            init.add(new SystemMessage("你是一个 helpful 的 AI 助手。"));
            return init;
        });

        messages.add(new UserMessage(userMessage));

        ChatResponse response = chatModel.call(new Prompt(messages));
        String answer = response.getResult().getOutput().getText();

        messages.add(new AssistantMessage(answer));
        log.info("用户 {} 的对话历史长度：{}", userId, messages.size());
        return answer;
    }

    public void clearMemory(String userId) {
        chatMemory.remove(userId);
        log.info("已清空用户 {} 的对话记忆", userId);
    }

    public String chatWithMcp(String userMessage) {
        log.info("收到MCP消息：{}", userMessage);

        var mcpTools = toolCallbackProvider.getToolCallbacks();
        log.info("MCP工具数量: {}", mcpTools != null ? mcpTools.length : 0);
        if (mcpTools != null) {
            for (var tool : mcpTools) {
                log.info("MCP工具: {} - {}", tool.getToolDefinition().name(), tool.getToolDefinition().description());
            }
        }

        String answer = chatClientBuilder
                .build()
                .prompt()
                .toolCallbacks(toolCallbackProvider)
                .system("你是一个智能助手，可以使用 MCP 提供的远程工具来回答问题。当用户询问天气、翻译、新闻等问题时，请调用对应的工具。")
                .user(userMessage)
                .call()
                .content();

        log.info("MCP AI 回复：{}", answer);
        return answer;
    }
}
