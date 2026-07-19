package com.yang.config;

import com.yang.chatmemory.FileBasedChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置
 * <p>
 * 恋爱大师 和 超级智能体 共用 FileBasedChatMemory：
 * - 文件持久化到 tmp/chat-memory/ 目录
 * - 按 chatId 隔离不同用户/模式的对话
 * - 重启服务不丢失
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        return new FileBasedChatMemory(fileDir);
    }
}
