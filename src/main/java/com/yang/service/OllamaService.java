package com.yang.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * Ollama 本地大模型服务
 * 学习：本地运行开源大模型，不依赖云服务
 */
@Slf4j
@Service
public class OllamaService {

    @Resource(name = "ollamaChatModel")
    private ChatModel ollamaChatModel;

    /**
     * 使用本地 Ollama 模型对话
     */
    public String chat(String message) {
        log.info("使用 Ollama 本地模型处理消息：{}", message);

        try {
            AssistantMessage response = ollamaChatModel.call(new Prompt(message))
                    .getResult()
                    .getOutput();

            String answer = response.getText();
            log.info("Ollama 回复：{}", answer);
            return answer;
        } catch (Exception e) {
            log.error("Ollama 调用失败", e);
            return "Ollama 调用失败：" + e.getMessage() + "\n\n请确保 Ollama 已启动！\n安装命令：https://ollama.com";
        }
    }

}