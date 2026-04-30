package com.yang.controller;

import com.yang.agent.ReActAgent;
import com.yang.model.dto.ChatRequest;
import com.yang.rag.LoveDocumentLoader;
import com.yang.service.AiService;
import com.yang.service.LoveAdvisorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI 聊天接口2", description = "AI 聊天相关接口2")
public class AiController2 {

    // ====== 注入 AiService ======
    @Resource
    private AiService aiService;

    // 注入 Agent
    @Resource
    private ReActAgent reActAgent;

    /**
     * 带工具调用的对话
     * 访问：http://localhost:8090/ai/chat/tools?message=现在几点了
     */
    @GetMapping("/chat/tools")
    @Operation(summary = "带工具调用的对话5", description = "AI 可以自动调用工具")
    public String chatWithTools(@RequestParam String message) {
        return aiService.chatWithTools(message);
    }


    /**
     * 快速测试接口（批量测试）
     */
    @GetMapping("/agent/test")
    @Operation(summary = "Agent 测试6", description = "测试 Agent 的各种能力")
    public String testAgent() {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>🧪 AI Agent 测试报告</h1>");

        // 测试1：问时间
        sb.append("<h3>测试1：问时间</h3>");
        sb.append("<p>输入：现在几点了？</p>");
        sb.append("<p>输出：").append(reActAgent.execute("现在几点了？")).append("</p>");

        // 测试2：查用户
        sb.append("<h3>测试2：查用户</h3>");
        sb.append("<p>输入：帮我查一下工号1001的员工信息</p>");
        sb.append("<p>输出：").append(reActAgent.execute("帮我查一下工号1001的员工信息")).append("</p>");

        // 测试3：计算
        sb.append("<h3>测试3：计算</h3>");
        sb.append("<p>输入：123乘以456等于多少？</p>");
        sb.append("<p>输出：").append(reActAgent.execute("123乘以456等于多少？")).append("</p>");

        return sb.toString();
    }

    @Resource
    private LoveAdvisorService loveAdvisorService;

    @Resource
    private LoveDocumentLoader documentLoader;

    /**
     * 初始化恋爱知识库
     * //以后用于多账号隔离的时候，创建的账号 默认初始化，通过账号的id
     */
    @GetMapping("/love/init")
    @Operation(summary = "初始化恋爱知识库7", description = "以后用于多账号隔离，创建的账号 默认初始化，通过账号的id存储不同的用户信息")
    public String initLoveKB() {
        loveAdvisorService.init();
        return "恋爱知识库初始化成功！";
    }


    /**
     * 恋爱顾问 RAG 问答
     * 访问：http://localhost:8090/ai/love/chat?question=不敢表白怎么办
     */
    @GetMapping("/love/chat")
    @Operation(summary = "上下文存储数据库，恋爱顾问7", description = "基于 RAG 的恋爱顾问")
    public String loveChat(@RequestParam String question) {
        return loveAdvisorService.chat(question);
    }

    /**
     * ReAct 循环 ,
     * 工具调用:4 个工具：查时间、查员工、计算器、查知识库
     * 短期记忆: ChatMemory 保持 20 条上下文
     * 长期记忆 对话精华存入 VectorStore，重启不丢
     * <p>
     * 思考 → 行动 → 观察 → 思考
     * 思考：“我需要知道用户的信息，然后计算他的入职天数，最后再去知识库里找对应的建议。”
     * 行动：它可以主动调用多个工具来获取信息
     * 观察：得到工具的返回结果。
     * 再思考，再行动：基于结果，决定下一步是继续调用工具，还是整合信息给出最终回答。
     */
    @GetMapping("/agent")
    @Operation(summary = "有记忆 能自主规划，调用工具 8", description = "+RAG+ReAct 思考 → 行动 → 观察 → 再思考 ")
    public String agentChat(@RequestParam String input) {
        // ReAct 多步推理：ChatClient 注册工具后自动执行思考→行动→观察循环
        // RAG 知识库检索：searchLoveKnowledge 工具已经加入 toolCallbacks，可被 Agent 调用
        return reActAgent.execute(input);
    }

    /**
     * 重新初始化知识库
     */
    @GetMapping("/love/reload")
    @Operation(summary = "清空所有数据，重新加载知识库", description = "清空并重新加载知识库")
    public String reloadLoveKB() {
        documentLoader.clearKnowledgeBase();
        loveAdvisorService.init();
        return "知识库重新加载成功！";
    }

    @PostMapping("/chat")
    @Operation(summary = "通用聊天接口", description = "前端调用的聊天接口")
    public String chat(@RequestBody ChatRequest request) {

        if ("agent".equals(request.getMode())) {
            // Agent 模式
            return reActAgent.execute(request.getMessage());
        } else if ("love".equals(request.getMode())) {
            // 恋爱顾问模式
            return loveAdvisorService.chat(request.getMessage());
        }

        return "";
    }
}