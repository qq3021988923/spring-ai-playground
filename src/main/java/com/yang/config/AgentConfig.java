package com.yang.config;

import com.yang.agent.ReActAgent;
import com.yang.tools.MyTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
 */
@Configuration
public class AgentConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
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