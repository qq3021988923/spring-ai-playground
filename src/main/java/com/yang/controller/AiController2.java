package com.yang.controller;

import com.yang.agent.YangManus;
import com.yang.model.dto.ChatRequest;
import com.yang.rag.LoveDocumentLoader;
import com.yang.service.LoveAdvisorService;
import com.yang.service.OllamaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 聊天接口2", description = "AI 聊天相关接口2")
public class AiController2 {

    @Resource
    private LoveAdvisorService loveAdvisorService;

    @Resource
    private OllamaService ollamaService;

    @Resource
    private YangManus yangManus;

    @GetMapping("/love/chat")
    @Operation(summary = "恋爱顾问", description = "支持状态过滤：单身/恋爱/已婚（不传则搜全部）")
    public String loveChat(@RequestParam String question,
                           @RequestParam(defaultValue = "user001") String userId,
                           @RequestParam(required = false) String status) {
        return loveAdvisorService.chat(question, "love_" + userId, status);
    }


    // 后续可以修改成 逐字打字机效果。现在还不是
    // 现在是后台默默干活，干完一次性甩给你完整结果 异步处理
    @GetMapping(value = "/manus/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE流式调用YangManus超级智能体 9")
    public SseEmitter doChatWithManus(
            @RequestParam(defaultValue = "user001") String userId,
            @RequestParam String message) {
        // ✅ 用userId作为会话ID，不同用户上下文完全隔离
        return yangManus.runStream(userId, message);
    }

    @PostMapping("/chat")
    @Operation(summary = "通用聊天接口8", description = "前端调用的聊天接口")
    public String chat(@RequestBody ChatRequest request,@RequestParam(defaultValue = "user001") String userId) {
        if ("agent".equals(request.getMode())) {
            return yangManus.run(request.getMessage(),userId); // 接口待优化： 对话上下文存储记忆
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

        return loveAdvisorService.chatStreamWithMultiQuery(message, "love_" + userId, null);
    }

    // 与上面的接口相比：结构化输出版返回 JSON 恋爱报告 。
    @GetMapping(value = "/love/chat/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "恋爱顾问（结构化报告）", description = "多Query RAG + 工具调用后，输出结构化JSON恋爱分析报告")
    public Object loveChatReport(String message, String userId) {
        return loveAdvisorService.chatWithStructuredReport(message, "love_" + userId, null);
    }

}