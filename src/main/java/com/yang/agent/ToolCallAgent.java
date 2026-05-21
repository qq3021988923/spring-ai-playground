package com.yang.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具调用智能体（完全还原你原来的逻辑）
 * 继承ReActAgent，实现：AI自动思考 → 调用工具 → 处理结果
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ToolCallAgent extends ReActAgent {
    // ==================== 工具相关配置 ====================
    private final ToolCallback[] availableTools;  // 所有可用工具
    private ChatResponse toolCallChatResponse;     // AI返回的工具调用响应
    private final ToolCallingManager toolCallingManager; // 工具执行管理器
    private final ChatOptions chatOptions;        // 大模型配置

    /**
     * 构造方法
     * @param availableTools 项目中所有注册的工具
     */
    public ToolCallAgent(ToolCallback[] availableTools) {
        this.availableTools = availableTools;
        // 初始化工具管理器
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 关闭自动执行工具，由我们手动控制
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    // ==================== 实现思考逻辑（完全还原你原来的代码） ====================
    @Override
    public boolean think() {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            getMessageList().add(new org.springframework.ai.chat.messages.UserMessage(getNextStepPrompt()));
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMsg = chatResponse.getResult().getOutput();
            // ========== 关键修复：必须把AI回答存入上下文 ==========
            getMessageList().add(assistantMsg);
            List<AssistantMessage.ToolCall> toolCallList = assistantMsg.getToolCalls();
            log.info("【{}】思考结果：{}", getName(), assistantMsg.getText());
            boolean hasToolCalls = !toolCallList.isEmpty();
            log.info("【{}】是否需要调用工具：{}", getName(), hasToolCalls);
            // 无工具直接强制结束
            if (!hasToolCalls) {
                setState(AgentState.FINISHED);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("思考过程异常", e);
            getMessageList().add(new AssistantMessage("思考异常：" + e.getMessage()));
            return false;
        }
    }

    // ==================== 实现行动逻辑（执行工具，只打日志不返回给前端） ====================
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            AssistantMessage lastAiMsg = (AssistantMessage) getMessageList().stream()
                    .filter(m -> m instanceof AssistantMessage)
                    .reduce((first, second) -> second)
                    .orElse(null);
            // ✅ 只有最终回答才返回给前端
            return lastAiMsg != null ? lastAiMsg.getText()
                    .replaceAll("\\{.*?\\}", "")
                    .replace("doTerminate","")
                    .replace("根据常识可知","⚠️ 无法联网，严格遵守规则不编造答案！")
                    .trim() : "暂无回答";
        }
        try {
            // 执行工具调用
            Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
            setMessageList(result.conversationHistory());

            // ✅ 工具结果只打后台日志，绝对不返回给前端
            ToolResponseMessage toolResponse = (ToolResponseMessage) getMessageList().get(getMessageList().size() - 1);
            log.info("【{}】工具执行结果：{}", getName(), toolResponse.getResponses());

            boolean isTerminate = toolResponse.getResponses().stream()
                    .anyMatch(res -> "doTerminate".equals(res.name()));

            // ✅ 任务结束：返回干净的最终回答
            if (isTerminate) {
                setState(AgentState.FINISHED);
                AssistantMessage lastAiMsg = (AssistantMessage) getMessageList().stream()
                        .filter(m -> m instanceof AssistantMessage)
                        .reduce((first, second) -> second)
                        .orElse(null);
                String cleanAnswer = lastAiMsg != null ?
                        lastAiMsg.getText()
                                .replaceAll("\\{.*?\\}", "")
                                .replace("doTerminate","")
                                .replace("根据常识可知","⚠️ 无法联网，严格遵守规则不编造答案！")
                                .trim()
                        : "任务已完成";
                return cleanAnswer;
            }

            // ✅ 工具执行完成，继续下一步思考，返回空（前端看不到任何内容）
            return "";
        } catch (Exception e) {
            log.error("工具执行异常", e);
            setState(AgentState.FINISHED);
            return "⚠️ 工具执行失败，严格遵守规则不编造答案！";
        }
    }
}