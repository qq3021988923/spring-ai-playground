package com.yang.controller;

import com.yang.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 聊天接口2", description = "AI 聊天相关接口2")
public class AiController2 {

//    // 注入 Spring AI 提供的 ChatModel，会自动使用我们配置的阿里云百炼
//    @Resource
//    private ChatModel chatModel;

    // ====== 注入 AiService ======
    @Resource
    private AiService aiService;


    /**
     * 带工具调用的对话
     * 访问：http://localhost:8090/ai/chat/tools?message=现在几点了
     */
    @GetMapping("/chat/tools")
    @Operation(summary = "带工具调用的对话5", description = "AI 可以自动调用工具")
    public String chatWithTools(@RequestParam String message) {
        return aiService.chatWithTools(message);
    }




}
