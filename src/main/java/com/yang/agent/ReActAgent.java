package com.yang.agent;

import com.yang.tools.MyTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * ReAct Agent
 * ReAct = Reasoning（推理） + Acting（行动）
 * 工作流程：思考 → 行动 → 观察 → 思考 → ... → 最终答案
 */
@Slf4j
public class ReActAgent extends BaseAgent {

    private final ChatClient chatClient;

    private final ToolCallback[] toolCallbacks;   // 注入 ToolCallback 数组


    public ReActAgent(ChatClient chatClient,  ToolCallback[] toolCallbacks) {
        super("智能助手小智", "一个会思考、会用工具的 AI 助手");
        this.chatClient = chatClient;
        this.toolCallbacks = toolCallbacks;
    }

    @Override
    public String execute(String userInput) {
        log("收到任务：" + userInput);
        log("========== Agent 开始工作 ==========");

        // 构建 System Prompt，明确告诉 Agent 如何工作
        String systemPrompt = """
            你是 %s，%s。
            
            你可以使用以下工具：
            - getCurrentTime() - 获取当前系统时间
            - queryUserInfo(userId) - 根据工号查询用户信息，例如 queryUserInfo("1001")
            - calculator(num1, operator, num2) - 计算器，支持 + - * /，例如 calculator(100, "+", 200)
            
            请按以下步骤工作：
            1. 先理解用户的需求
            2. 决定是否需要使用工具
            3. 如果需要，直接调用工具获取结果
            4. 整合所有信息，给出最终答案
            
            注意：
            - 如果问题与时间相关，先调用 getCurrentTime()
            - 如果需要查询员工信息，调用 queryUserInfo()
            - 如果需要计算，调用 calculator()
            - 不需要解释你的思考过程，直接给出最终答案
            """.formatted(agentName, agentDescription);

        // 调用 AI（带工具支持）
        String finalAnswer = chatClient.prompt()
                .system(systemPrompt)
                .user(userInput)
                .toolCallbacks(toolCallbacks)   // ✅ 直接传数组，绕过扫描
                .call()
                .content();

        log("Agent 完成任务！");
        log("========== Agent 结束工作 ==========");
        return finalAnswer;
    }

}