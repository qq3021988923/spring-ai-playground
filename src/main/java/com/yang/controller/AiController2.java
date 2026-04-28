package com.yang.controller;

import com.yang.agent.ReActAgent;
import com.yang.rag.LoveDocumentLoader;
import com.yang.service.AiService;
import com.yang.service.LoveAdvisorService;
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
     * AI Agent 对话接口
     * 访问地址：http://localhost:8090/ai/agent?input=现在几点了
     */
    @GetMapping("/agent")
    @Operation(summary = "AI Agent 对话6", description = "使用 ReAct Agent 处理任务")
    public String agentChat(@RequestParam String input) {
        return reActAgent.execute(input);
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
     */
    @GetMapping("/love/init")
    @Operation(summary = "初始化恋爱知识库7", description = "加载恋爱知识库到向量数据库")
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

}
