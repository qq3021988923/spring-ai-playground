package com.yang.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentConfig {

    // ==================== DeepSeek Flash 配置（从 yml 复用 API Key 和 Base URL） ====================
    @Value("${spring.ai.openai.api-key}")
    private String deepseekApiKey;

    @Value("${spring.ai.openai.base-url}")
    private String deepseekBaseUrl;

    /**
     * DeepSeek V4 Flash — 快速轻量模型
     * 适用场景：简单问答、意图识别、格式转换、闲聊
     * 不适用：多步工具调用、复杂推理（交给 Pro）
     */
    @Bean
    public ChatModel deepseekFlashChatModel() {
        var openAiApi = OpenAiApi.builder()
                .apiKey(deepseekApiKey)
                .baseUrl(deepseekBaseUrl)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.3)
                        .build())
                .build();
    }

    @Bean //保留
    public ChatClient flashChatClient(@Qualifier("deepseekFlashChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // ==================== 原有配置 ====================

    @Bean
    public ChatClient.Builder dashscopeChatClientBuilder(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    public ChatClient.Builder ollamaChatClientBuilder(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    // DeepSeek V4 Pro 作为主模型（openAiChatModel 由 spring-ai-starter-openai 自动创建）
    // Agent 推理链（think → act）必须用 Pro，Flash 只做轻量任务
    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    public ChatClient chatClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
