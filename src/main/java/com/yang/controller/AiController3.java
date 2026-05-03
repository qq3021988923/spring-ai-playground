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
     * Ollama 本地模型对话 没有云端execute (云端 Agent)的强大 本地小模型 (如 qwen2.5:0.5b)
     * 复杂推理能力  弱， 响应速度 取决于电脑配置 不过免费（不需要接入第三方api接口） 离线
     * 访问：http://localhost:8090/ai3/ollama?message=你好
     */
    @GetMapping("/ollama")
    @Operation(summary = "Ollama 9", description = "使用本地 Ollama 大模型 Agent，集成工具、记忆、RAG 知识库 没有云端execute (云端 Agent)的强大 本地小模型 (如 qwen2.5:0.5b)\n" +
            "    复杂推理能力  弱， 响应速度 取决于电脑配置 不过免费（不需要接入第三方api接口） 离线")
    public String ollamaChat(@RequestParam String message) {
        return ollamaService.fullAgentChat(message);
    }




}
