package com.yang.controller;


import com.yang.service.OllamaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/ai3")
@Tag(name = "AI 聊天接口3", description = "AI 聊天相关接口3")
public class AiController3 {



    @Resource
    private OllamaService ollamaService;

    /**
     * Ollama 本地模型对话
     * 访问：http://localhost:8090/ai/ollama?message=你好
     */
    @GetMapping("/ollama")
    @Operation(summary = "Ollama 本地模型9", description = "使用本地 Ollama 大模型")
    public String ollamaChat(@RequestParam String message) {
        return ollamaService.chat(message);
    }




}
