package com.yang.config;

import com.yang.agent.YangManus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentConfig {
    // Spring 配置类   集中管理 Bean 注册
    @Bean
    public ChatClient.Builder dashscopeChatClientBuilder(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    public ChatClient.Builder ollamaChatClientBuilder(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder(@Qualifier("dashscopeChatClientBuilder") ChatClient.Builder builder) {
        return builder;
    }

    @Bean
    public ChatClient chatClient(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public YangManus yangManus(
            ToolCallback[] toolCallbacks, //   所有工具
            @Qualifier("dashscopeChatModel") ChatModel chatModel,  // 明确指定用阿里云百炼，不用 Ollama
            ChatMemory chatMemory, // 上下文记忆
            VectorStore vectorStore // 向量库
    ) {
        return new YangManus(toolCallbacks, chatModel, chatMemory, vectorStore);
    }
}
