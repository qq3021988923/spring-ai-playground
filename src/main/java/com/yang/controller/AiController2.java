package com.yang.controller;

import com.yang.agent.ReActAgent;
import com.yang.agent.YangManus;
import com.yang.model.dto.ChatRequest;
import com.yang.rag.LoveDocumentLoader;
import com.yang.service.AiService;
import com.yang.service.LoveAdvisorService;
import com.yang.service.OllamaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 聊天接口2", description = "AI 聊天相关接口2")
public class AiController2 {

    @Resource
    private AiService aiService;

    // 旧Agent（保留不动）
    @Resource
    private ReActAgent reActAgent;

    @Resource
    private LoveAdvisorService loveAdvisorService;

    @Resource
    private LoveDocumentLoader documentLoader;

    @Resource
    private OllamaService ollamaService;

    // ===================== 核心：注入你的超级智能体 =====================
    @Resource
    private YangManus yangManus;

    @GetMapping("/chat/tools")
    @Operation(summary = "带工具调用的对话5", description = "AI 可以自动调用工具")
    public String chatWithTools(@RequestParam String message) {
        return aiService.chatWithMcp(message);
    }

//    @GetMapping("/love/init")
//    @Operation(summary = "初始化恋爱知识库7", description = "初始化恋爱知识库")
//    public String initLoveKB() {
//        loveAdvisorService.init();
//        return "恋爱知识库初始化成功！";
//    }

    @GetMapping("/love/chat")
    @Operation(summary = "上下文存储数据库，恋爱顾问7", description = "基于 RAG 的恋爱顾问")
    public String loveChat(@RequestParam String question) {
        return loveAdvisorService.chat(question);
    }

    // 原有旧Agent接口（保留）
    @GetMapping("/agent")
    @Operation(summary = "有记忆 能自主规划，调用工具 8", description = "ReAct Agent + MCP 工具")
    public String agentChat(@RequestParam String input) {
        return reActAgent.run(input);
    }

    // ===================== ✅ 修复完成：可直接运行的SSE流式接口 =====================
    @GetMapping(value = "/manus/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE流式调用YangManus超级智能体 9")
    public SseEmitter doChatWithManus(@RequestParam String message) {
        // 直接调用YangManus的流式方法，100%基于你现有代码
        return yangManus.runStream(message);
    }

//    @GetMapping("/love/reload")
//    @Operation(summary = "清空所有数据，重新加载知识库", description = "清空并重新加载知识库")
//    public String reloadLoveKB() {
//        documentLoader.clearKnowledgeBase();
//        return "知识库重新加载成功！";
//    }

    @PostMapping("/chat")
    @Operation(summary = "通用聊天接口8", description = "前端调用的聊天接口")
    public String chat(@RequestBody ChatRequest request) {
        if ("agent".equals(request.getMode())) {
            return reActAgent.run(request.getMessage());
        } else if ("love".equals(request.getMode())) {
            return loveAdvisorService.chat(request.getMessage());
        } else if ("ollama".equals(request.getMode())) {
            return ollamaService.fullAgentChat(request.getMessage());
        }
        return "";
    }
}