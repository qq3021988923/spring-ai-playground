package com.yang.config;

import com.yang.service.JokeService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class LangChain4jConfig {

    private static final String API_KEY = "sk-8f196f4a6a394c248c15808a619a85ee";
    private static final String MODEL_NAME = "qwen-plus";

    @Bean
    public QwenChatModel qwenChatModel() {
        return QwenChatModel.builder()
                .apiKey(API_KEY)   // 替换为你的阿里云 API Key
                .modelName(MODEL_NAME)
                .build();
    }

    @Bean
    public JokeService jokeService(QwenChatModel qwenChatModel) {
        // 为每个会话ID创建独立的记忆窗口，最多保留20条消息
        ChatMemoryProvider memoryProvider = memoryId ->
                MessageWindowChatMemory.withMaxMessages(20);

        return AiServices.builder(JokeService.class)
                .chatLanguageModel(qwenChatModel)
                .chatMemoryProvider(memoryProvider)  // 启用临时记忆
                .build();
    }


}