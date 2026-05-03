package com.yang.config;

import com.yang.agent.ReActAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentConfig {

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
    public ReActAgent reActAgent(ChatClient chatClient,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider mcpToolProvider, VectorStore vectorStore) {
        return new ReActAgent(chatClient, chatMemory, mcpToolProvider,vectorStore);
    }
}
