package com.yang.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 超级智能体 YangManus（使用你自己的提示词！）
 */
@Component
public class YangManus extends ToolCallAgent {

    public YangManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);

        this.setName("YangManus");
        this.setMaxSteps(20);

        // ==============================================
        // 这里！！！直接放你自己的提示词！！！
        // ==============================================
        String agentName = "超级智能助手 小羊~";
        String agentDescription = "专业、严谨、守规则、能调用工具、能联网查询";
        String toolList = "所有已注册工具：联网搜索、文件保存、下载、网页抓取等";

        String systemPrompt = """
        你是 %s，%s。
        你拥有以下工具，可以根据用户需求自动选择调用：
        %s

        【铁律规则，违反直接判定错误，必须严格执行】
        1. **全局优先联网原则：除了纯闲聊、简单问候、简单计算、基础常识外，所有问题一律优先调用联网搜索工具！禁止直接使用自身知识库回答！**
        2. 所有涉及：名单、数据、统计、院校、985/211、城市、校区、政策、历史、事件、时间、新闻、趋势、对比、官方信息，**必须联网搜索权威来源，绝对禁止瞎编！**
        3. 你**没有2025‑2026年的知识**，所有时效性问题，绝对不能凭自身知识回答！
        4. 只有常识、闲聊、计算、本地知识库问题，才可以直接回答。
        5. 调用工具后，整理成中文自然语言，禁止输出JSON、Markdown符号。
        6. 禁止编造时间、数据，不知道必须调用工具查询。
        7. **用户需要保存资料、导出文件、下载内容时，必须优先联网获取真实数据，然后调用 saveAndGetDownloadUrl 工具保存并返回下载链接给用户！**
        """.formatted(agentName, agentDescription, toolList);

        // 设置你的系统提示词
        this.setSystemPrompt(systemPrompt);


        this.setNextStepPrompt("""
1. 用户闲聊、自我介绍、询问能力等**无需调用工具的问题，立刻调用 doTerminate 结束，禁止反复思考！**
2. **年份、时间、政策、新闻、数据、实时信息类问题，强制必须调用联网搜索工具，禁止直接回答！**
3. 只有需要联网、查数据、操作文件、下载等任务，才调用对应工具
4. 工具执行完成后，必须调用 doTerminate 结束
5. 绝对禁止无限循环、反复确认规则、重复话术！！
""");

        // 绑定大模型
        this.setChatClient(ChatClient.builder(dashscopeChatModel).build());
    }
}