package com.yang.controller;

import com.yang.agent.YangManus;
import com.yang.model.dto.ChatRequest;
import com.yang.service.LoveAdvisorService;
import com.yang.service.OllamaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
    @Operation(summary = "上下文存储数据库，恋爱顾问7", description = "基于 RAG 的恋爱顾问")
    public String loveChat(@RequestParam String question,
                           @RequestParam(defaultValue = "user001") String userId) {
        return loveAdvisorService.chat(question, "love_" + userId);
    }

//    // 原有旧Agent接口（保留，添加userId参数） 待优化
//    @GetMapping("/agent") //
//    @Operation(summary = "有记忆 能自主规划，调用工具 8", description = "" +
//            "ReAct 思考 - 行动模式。工具调用（手动管控）。上下文短期记忆（多轮对话）。状态机管理（防死循环）。智能体人设 / 规则约束。联网搜索强制规则。将数据存储本地数据库")
//    public String agentChat(
//            @RequestParam(defaultValue = "user001") String userId,
//            @RequestParam String input) {
//        // ✅ 用userId作为会话ID，不同用户上下文完全隔离
//        return yangManus.run(userId, input);
//    }

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
            return loveAdvisorService.chatWithTools(request.getMessage(),charId); // .call()
        } else if ("ollama".equals(request.getMode())) {
            return ollamaService.fullAgentChat(request.getMessage());
        }
        return "";
    }

    // 流式版（新增）
    @GetMapping(value = "/love/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> loveChatStream(String message, String userId) {
        return loveAdvisorService.chatStream(message, "love_" + userId);
    }

}