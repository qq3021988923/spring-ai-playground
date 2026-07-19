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
import org.springframework.ai.chat.messages.UserMessage;
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
    // 为场景特殊（final + 数组 + 抽象父类），才用构造注入 本质还是 Spring 自动注入
    // 加 final + 构造方法里赋值 = spring 自动注入
    private final ToolCallback[] availableTools;  // 所有可用工具
    private ChatResponse toolCallChatResponse;     //专门存储「AI 返回的、包含工具调用指令的完整响应结果」
    private final ToolCallingManager toolCallingManager; // 工具执行管理器
    private final ChatOptions chatOptions;        // 大模型配置

    /**
     * 构造方法
     * @param availableTools 项目中所有注册的工具
     */
    public ToolCallAgent(ToolCallback[] availableTools) { //  AI 工具清单（告诉它：你有这些工具能用）
        this.availableTools = availableTools;
        //  工具调用大管家
        this.toolCallingManager = ToolCallingManager.builder().build();

        // 不许框架偷偷跑工具，工具调用的所有步骤全交给我自己代码来管。
        // ReAct 智能体 = 工具调用的总指挥
        // AI 思考（think）：模型说「我需要查资料 / 计算 / 调用工具」
        //你的代码手动触发（act）：这时候才调用工具！
        //拿到工具结果 → 再发给 AI   最终回答用户
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false) //  框架不帮你调用工具  靠这个 toolCallingManager 来调！
                .build();
    }

    // 思考：AI 决定要不要调用工具
    @Override
    public boolean think() {
        // 不为空进入
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
        }
        List<Message> messageList = getMessageList();
    // this.chatOptions 调用的是public ToolCallAgent这个方法洗礼之后的属性 默认this.chatOptions AI关闭自动工具调用
        // 把「对话内容」和「AI 行为规则」打包成一个「完整请求包」，发给 AI 大模型！
        // AI 只认识 Prompt 这个对象，没有它，你根本调不动 AI！
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools) // 会在 think() 里自己判断：要不要调用、调用哪个；
                    .call()
                    .chatResponse();
            this.toolCallChatResponse = chatResponse;

            // 从 AI 返回的完整结果中，把 AI 回复的消息单独拿出来！
            // assistantMsg 这个对象有 toolCalls 如果不为空 就说明是需要调用工具的！
            AssistantMessage assistantMsg = chatResponse.getResult().getOutput();

            getMessageList().add(assistantMsg); //  必须临时存上下文
            List<AssistantMessage.ToolCall> toolCallList = assistantMsg.getToolCalls();
            log.info("【{}】思考结果：{}", getName(), assistantMsg.getText());
            boolean hasToolCalls = !toolCallList.isEmpty(); // 取反 → 列表不为空 → AI需要调用工具
            log.info("【{}】是否需要调用工具：{}", getName(), hasToolCalls);

            if (hasToolCalls) {
                for (AssistantMessage.ToolCall tc : toolCallList) {
                    pushStatus("[TOOL] 调用工具：" + tc.name());
                }
            }

            if (!hasToolCalls) {
                // 在当前内部类 可以省略this.
                setState(AgentState.FINISHED); // 无工具直接结束
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("思考过程异常", e);
            getMessageList().add(new AssistantMessage("思考异常：" + e.getMessage()));
            return false;
        }
    }

     // 用 `ToolCallingManager` 执行工具
    /*
    如果需要工具，执行联网/保存文件
    如果不需要，直接生成最终答案
    执行完成后标记任务结束
    优先级：思考完，在执行行动
    * */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) { // 有内容 就是true，然后取反 false就不会进入if
            AssistantMessage lastAiMsg = (AssistantMessage) getMessageList().stream()
                    .filter(m -> m instanceof AssistantMessage)
                    .reduce((first, second) -> second)
                    .orElse(null);
            //   只有最终回答才返回给前端
            return lastAiMsg != null ? lastAiMsg.getText()
                    .replaceAll("\\{.*?\\}", "")
                    .replace("doTerminate","")
                    .replace("根据常识可知","⚠️ 无法联网，严格遵守规则不编造答案！")
                    .trim() : "暂无回答";
        }
        try {
            // 就是把「最新的聊天记录」+「你早就定好的 AI 规则（关闭自动调用）」打包在一起
            Prompt prompt = new Prompt(getMessageList(), this.chatOptions);

            // 用你创建的 toolCallingManager 执行工具，真正开始手动调用工具
            // 这里面就有用户原始提问 ,全局系统提示词 ,附加业务规则（任务终止规则、数据查询限制）
            // AI 返回的工具调用指令，携带 searchWeb 调用参数 , 工具执行新增消息
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
            setMessageList(result.conversationHistory());

            //   工具结果只打后台日志，绝对不返回给前端  取出消息列表最后一条消息（刚执行完工具新增的ToolResponseMessage）
            ToolResponseMessage toolResponse = (ToolResponseMessage) getMessageList().get(getMessageList().size() - 1);
            // 打印日志：输出当前Agent名称 + 工具完整返回结果（包含是否报错、返回内容、工具调用ID）
            log.info("【{}】工具执行结果：{}", getName(), toolResponse.getResponses());
            // 推送工具结果给前端
            for (ToolResponseMessage.ToolResponse r : toolResponse.getResponses()) {
                String preview = r.responseData().length() > 100
                        ? r.responseData().substring(0, 100) + "..."
                        : r.responseData();
                pushStatus("[TOOL] " + r.name() + " 完成 → " + preview);
            }

            // 标记当前 Agent 循环是否该终止
            // toolResponse.getResponses()：拿到本次全部工具执行结果集合（一次可并行调用多个工具）
            // .stream()：转为流式遍历工具结果
// .anyMatch(...)：短路判断，只要任意一个工具的名称等于 doTerminate，直接返回 true 全部都不是则返回 false
            boolean isTerminate = toolResponse.getResponses().stream()
                   //  如果有一个工具是 doTerminate 这个，说明这次对否结束！
                    .anyMatch(res -> "doTerminate".equals(res.name()));

            //   任务结束：返回干净的最终回答
            if (isTerminate) {
                setState(AgentState.FINISHED);
                AssistantMessage lastAiMsg = (AssistantMessage) getMessageList().stream()
                       //  // 只筛选 AI 的回复消息
                        .filter(m -> m instanceof AssistantMessage)
                        //  // 取【最后一条】AI 消息
                        .reduce((first, second) -> second)
                        // // 没找到就返回 null
                        .orElse(null);

                String cleanAnswer = lastAiMsg != null ?
                        lastAiMsg.getText()
                                .replaceAll("\\{.*?\\}", "")  // 1. 删除所有大括号{}里的内容（工具参数）
                                .replace("doTerminate","")  //   2. 删除「结束对话」工具关键词
                                // 3. 替换无用话术
                                .replace("根据常识可知","⚠️ 无法联网，严格遵守规则不编造答案！")
                                .trim()
                        : "任务已完成";
                return cleanAnswer;
            }

            //   工具执行完成，继续下一步思考，返回空（前端看不到任何内容）
            return "";
        } catch (Exception e) {
            log.error("工具执行异常", e);
            setState(AgentState.FINISHED);
            return "⚠️ 工具执行失败，严格遵守规则不编造答案！";
        }
    }
}
