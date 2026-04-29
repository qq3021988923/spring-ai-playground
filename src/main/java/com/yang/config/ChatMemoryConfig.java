package com.yang.config;


import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        // 1. 创建底层存储仓库，负责数据存取
        InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();

        // 2. 创建滑动窗口记忆管理器，设置最大保留 20 条消息，防止 Token 超限
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }
}
