package com.yang.config;

import com.yang.agent.ReActAgent;
import com.yang.tools.MyTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
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

    @Bean
    public ReActAgent reActAgent(ChatClient chatClient, ToolCallback[] toolCallbacks) {
        return new ReActAgent(chatClient, toolCallbacks);
    }


}