package com.yang.controller;

import com.yang.agent.ReActAgent;
import com.yang.model.dto.ChatRequest;
import com.yang.rag.LoveDocumentLoader;
import com.yang.service.AiService;
import com.yang.service.LoveAdvisorService;
import com.yang.service.OllamaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 聊天接口2", description = "AI 聊天相关接口2")
public class AiController2 {

    @Resource
    private AiService aiService;

    @Resource
    private ReActAgent reActAgent;

    @Resource
    private LoveAdvisorService loveAdvisorService;

    @Resource
    private LoveDocumentLoader documentLoader;

    @Resource
    private OllamaService ollamaService;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @GetMapping("/chat/tools")
    @Operation(summary = "带工具调用的对话5", description = "AI 可以自动调用工具")
    public String chatWithTools(@RequestParam String message) {
        return aiService.chatWithMcp(message);
    }

    @GetMapping("/mcp/test")
    @Operation(summary = "MCP工具测试", description = "测试 MCP 第三方工具是否可用")
    public String mcpTest(@RequestParam String message) {
        log.info("=== MCP 测试接口被调用 ===");
        var tools = toolCallbackProvider.getToolCallbacks();
        log.info("MCP工具数量: {}", tools.length);
        for (var tool : tools) {
            log.info("可用工具: {} - {}", tool.getToolDefinition().name(), tool.getToolDefinition().description());
        }
        return aiService.chatWithMcp(message);
    }

    @GetMapping("/agent/test")
    @Operation(summary = "Agent 测试6", description = "测试 Agent 的各种能力")
    public String testAgent() {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>🧪 AI Agent 测试报告</h1>");

        sb.append("<h3>测试1：MCP 天气查询</h3>");
        sb.append("<p>输入：查询北京天气</p>");
        sb.append("<p>输出：").append(reActAgent.execute("查询北京天气")).append("</p>");

        sb.append("<h3>测试2：MCP 翻译</h3>");
        sb.append("<p>输入：把「你好世界」翻译成英语</p>");
        sb.append("<p>输出：").append(reActAgent.execute("把你好世界翻译成英语")).append("</p>");

        return sb.toString();
    }

    @GetMapping("/love/init")
    @Operation(summary = "初始化恋爱知识库7", description = "初始化恋爱知识库")
    public String initLoveKB() {
        loveAdvisorService.init();
        return "恋爱知识库初始化成功！";
    }

    @GetMapping("/love/chat")
    @Operation(summary = "上下文存储数据库，恋爱顾问7", description = "基于 RAG 的恋爱顾问")
    public String loveChat(@RequestParam String question) {
        return loveAdvisorService.chat(question);
    }

    @GetMapping("/agent")
    @Operation(summary = "有记忆 能自主规划，调用工具 8", description = "ReAct Agent + MCP 工具")
    public String agentChat(@RequestParam String input) {
        return reActAgent.execute(input);
    }

    @GetMapping("/love/reload")
    @Operation(summary = "清空所有数据，重新加载知识库", description = "清空并重新加载知识库")
    public String reloadLoveKB() {
        documentLoader.clearKnowledgeBase();
        loveAdvisorService.init();
        return "知识库重新加载成功！";
    }

    @PostMapping("/chat")
    @Operation(summary = "通用聊天接口8", description = "前端调用的聊天接口")
    public String chat(@RequestBody ChatRequest request) {
        if ("agent".equals(request.getMode())) {
            return reActAgent.execute(request.getMessage());
        } else if ("love".equals(request.getMode())) {
            return loveAdvisorService.chat(request.getMessage());
        } else if ("ollama".equals(request.getMode())) {
            return ollamaService.fullAgentChat(request.getMessage());
        }
        return "";
    }
}
