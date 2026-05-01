package com.yang.config;

import com.yang.agent.ReActAgent;
import com.yang.tools.MyTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Agent 配置类
 */
@Configuration
public class AgentConfig {


    // 1. 创建阿里云百炼的 ChatClient.Builder
    @Bean
    public ChatClient.Builder dashscopeChatClientBuilder(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    // 2. 创建 Ollama 的 ChatClient.Builder
    @Bean
    public ChatClient.Builder ollamaChatClientBuilder(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    // 3. 声明一个默认的 ChatClient.Builder（给 ReActAgent 或 LoveAdvisorService 用）
    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder(@Qualifier("dashscopeChatClientBuilder") ChatClient.Builder builder) {
        return builder;
    }


    @Bean
    public ChatClient chatClient(@Qualifier("dashscopeChatModel")ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }


    /*
    * 把 ChatClient（大脑）、ToolCallbacks[]（工具箱）、
    * ChatMemory（短期记忆）、VectorStore（长期知识库）全
    * 部注入到 ReActAgent。
    * */
    @Bean
    public ReActAgent reActAgent(ChatClient chatClient,
                                 ToolCallback[] toolCallbacks,
                                 ChatMemory chatMemory,
                                 VectorStore vectorStore) {
        return new ReActAgent(chatClient, toolCallbacks, chatMemory, vectorStore);
    }


}