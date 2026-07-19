package com.yang.config;

import com.yang.agent.YangManus;
import com.yang.rag.QueryExpander;
import com.yang.rag.QueryRewriter;
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
            ToolCallback[] toolCallbacks,
            @Qualifier("dashscopeChatModel") ChatModel chatModel,
            ChatMemory chatMemory,
            VectorStore vectorStore,
            QueryExpander queryExpander,
            QueryRewriter queryRewriter
    ) {
        return new YangManus(toolCallbacks, chatModel, chatMemory, vectorStore, queryExpander, queryRewriter);
    }
}
