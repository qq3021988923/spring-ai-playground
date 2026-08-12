package com.yang.controller;

import com.yang.agent.YangManus;
import com.yang.model.dto.ChatRequest;
import com.yang.rag.LoveDocumentLoader;
import com.yang.rag.QueryExpander;
import com.yang.rag.QueryRewriter;
import com.yang.service.LoveAdvisorService;
import com.yang.service.OllamaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 聊天接口2", description = "AI 聊天相关接口2")
public class AiController2 {

    @Resource
    private LoveAdvisorService loveAdvisorService;

    @Resource
    private OllamaService ollamaService;

    // Agent 每次请求新建实例所需的依赖
    @Resource
    private ToolCallback[] allTools;
    @Resource
    @Qualifier("openAiChatModel")
    private ChatModel deepseekChatModel;        // DeepSeek V4 Pro（Agent 推理）
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private VectorStore vectorStore;
    @Resource
    private QueryExpander queryExpander;
    @Resource
    private QueryRewriter queryRewriter;

    @GetMapping("/love/chat")
    @Operation(summary = "恋爱顾问", description = "支持状态过滤：单身/恋爱/已婚（不传则搜全部）")
    public String loveChat(@RequestParam String question,
                           @RequestParam(defaultValue = "user001") String userId,
                           @RequestParam(required = false) String status) {
        return loveAdvisorService.chat(question, "love_" + userId, status);
    }



    @GetMapping(value = "/manus/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE流式调用YangManus超级智能体 9")
    public String doChatWithManus(
            @RequestParam(defaultValue = "user001") String userId,
            @RequestParam String message) {
        // 参考鱼皮项目：每次请求新建 Agent 实例，避免状态污染
        YangManus agent = new YangManus(allTools, deepseekChatModel, chatMemory, vectorStore, queryExpander, queryRewriter);
        return agent.runString(userId, message);
    }

    @PostMapping("/chat")
    @Operation(summary = "通用聊天接口8", description = "前端调用的聊天接口")
    public String chat(@RequestBody ChatRequest request, @RequestParam(defaultValue = "user001") String userId) {
        if ("agent".equals(request.getMode())) {
            YangManus agent = new YangManus(allTools, deepseekChatModel, chatMemory, vectorStore, queryExpander, queryRewriter);
            return agent.run(userId, request.getMessage());
        } else if ("love".equals(request.getMode())) {
            String charId = "love_" + userId; // 统一用小写 love_，和 SSE 接口保持一致
            return loveAdvisorService.chatWithTools(request.getMessage(), charId, null);
        } else if ("ollama".equals(request.getMode())) {
            return ollamaService.fullAgentChat(request.getMessage());
        }
        return "";
    }

    @Autowired
    private LoveDocumentLoader documentLoader;

    // 文件下载接口（AI 生成的文件通过这个下载）
    @GetMapping("/download")
    @Operation(summary = "下载AI生成的文件")
    public void downloadFile(@RequestParam String fileName, HttpServletResponse response) throws IOException {
        String filePath = System.getProperty("user.dir") + "/tmp/file/" + fileName;
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            response.setStatus(404);
            response.getWriter().write("文件不存在");
            return;
        }
        response.setContentType("application/octet-stream; charset=UTF-8");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        try (FileInputStream fis = new FileInputStream(file); OutputStream os = response.getOutputStream()) {
            fis.transferTo(os);
        }
    }

    // 用户级：只清当前用户的记忆 + 数据库记录
    @PostMapping("/admin/reset")
    @Operation(summary = "清空我的缓存", description = "只清当前用户的文件记忆和对话记录")
    public String resetMyCache(@RequestParam(defaultValue = "unknown") String userId) {
        return loveAdvisorService.resetMyCache(userId);
    }

    // 系统级：清空所有数据 + 重载知识库（需密码）
    @PostMapping("/admin/reset-all")
    @Operation(summary = "重置系统（需密码）", description = "清空所有文件记忆、向量库，重新加载知识库")
    public String resetAllSystem(@RequestParam(defaultValue = "") String pwd) {
        if (!"123456".equals(pwd)) {
            return "密码错误，操作已取消";
        }
        return loveAdvisorService.resetAllSystem();
    }

    // 流式版（单 Query RAG + 工具调用）
    @GetMapping(value = "/love/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> loveChatStream(String message, String userId) {

      // documentLoader.clearKnowledgeBase();

        return loveAdvisorService.chatStream(message, "love_" + userId, null);
    }

    // 流式多Query扩展版（多Query检索 + 工具调用）
    /*
    love/chat/sse	    /love/chat/sse/multi-query
    查数据库次数	1 次	4 次
    检索文档上限	3 条	12 条（去重前）
    额外大模型调用	无	    +1 次（Query 扩展）
    额外耗时	无	        ~30ms（3 次额外 HNSW 查询）
    * */
    @GetMapping(value = "/love/chat/sse/multi-query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "恋爱顾问（多Query扩展）", description = "单问题扩展为4个变体并行检索，去重合并后回答")
    public Flux<String> loveChatStreamMultiQuery(String message, String userId) {
        // documentLoader.clearKnowledgeBase();  // 清空数据库 可以按时间定时清空数据库
        return loveAdvisorService.chatStreamWithMultiQuery(message, "love_" + userId, null);
    }

    // 与上面的接口相比：结构化输出版返回 JSON 恋爱报告 。
    @GetMapping(value = "/love/chat/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "恋爱顾问（结构化报告）", description = "多Query RAG + 工具调用后，输出结构化JSON恋爱分析报告")
    public Object loveChatReport(String message, String userId) {

        return loveAdvisorService.chatWithStructuredReport(message, "love_" + userId, null);
    }

}