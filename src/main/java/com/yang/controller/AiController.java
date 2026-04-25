package com.yang.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 聊天控制器
 * 学习第1步：使用 Spring AI 调用阿里云百炼大模型
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 聊天接口", description = "AI 聊天相关接口")
public class AiController {

    // 注入 Spring AI 提供的 ChatModel，会自动使用我们配置的阿里云百炼
    @Resource
    private ChatModel chatModel;

    /**
     * 最简单的调用：一问一答 直接使用这个就可以了
     * 访问地址：http://localhost:8090/ai/chat?message=你好
     */
    @GetMapping("/chat")
    @Operation(summary = "简单聊天", description = "输入一句话，AI 回复")
    public String chat(@RequestParam String message) {
        log.info("收到用户消息：{}", message);

        // 调用 AI 模型
        ChatResponse response = chatModel.call(new Prompt(message));
        log.info("用 AI 模型响应体：{}", response);

        // 获取 AI 的回复内容
        String answer = response.getResult().getOutput().getText();
        log.info("AI 回复：{}", answer);

        return answer;
    }

    /**
     * 多轮对话：支持上下文
     * 访问地址：http://localhost:8090/ai/chat/multi
     */
    @PostMapping("/chat/multi")
    @Operation(summary = "多轮对话", description = "支持上下文的对话")
    public String multiChat(@RequestBody List<String> messages) {
        log.info("收到多轮对话消息：{}", messages);

        // 构建消息列表
        List<Message> chatMessages = new ArrayList<>();

        // 遍历输入的消息，交替添加用户消息和助手消息
        for (int i = 0; i < messages.size(); i++) {
            if (i % 2 == 0) {
                // 偶数位置是用户说的内容
                chatMessages.add(new UserMessage(messages.get(i)));
            } else {
                // 奇数位置是 AI 回复的内容
                chatMessages.add(new AssistantMessage(messages.get(i)));
            }
        }

        // chatModel.call 调用AI模型。返回一个包含生成结果和元数据（如 token 用量、ID 等）
        ChatResponse response = chatModel.call(new Prompt(chatMessages));

// response.getResult() 取出 AI 的核心回答部分。Generation 对象。如果只有一条回复，就是取第一个 Generation 对象
// .getOutput()拿到 AI 的输出消息，这是一个 AssistantMessage 对象，里面包含 AI 说的话。
// .getText() 从 AssistantMessage 里把纯文字内容提取出来
        String answer = response.getResult().getOutput().getText();
        AssistantMessage output = response.getResult().getOutput();
        log.info("AI 回复：{}", answer);
        return answer;
    }

}