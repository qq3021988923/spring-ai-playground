
package com.yang.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**

 功能：流式输出 + 上下文记忆 + 向量库存储 + 工具调用 + 多步思考
 */
@Component
public class YangManus extends ToolCallAgent {


    // 只要调用这个类 就会优先执行这个方法  构造方法
    public YangManus(ToolCallback[] allTools,
                     ChatModel dashscopeChatModel,
                     ChatMemory chatMemory,
                     VectorStore vectorStore) {
        super(allTools);
        // 基础配置
        this.setName("咩~");
        this.setMaxSteps(7);
        // ✅ 注入上下文记忆（多轮对话）
        this.setChatMemory(chatMemory);
        // ✅ 注入向量库（长期存储）
        this.setVectorStore(vectorStore);
        // 绑定大模型
        this.setChatClient(ChatClient.builder(dashscopeChatModel).build());

        String agentName = "你是超级智能助手 小羊~，专业、严谨、守规则、能调用工具、能联网查询。";

        // 定义智能体人设/规则，约束 AI 行为
        String systemPrompt = """
 %s。
【核心目标】：用户问任何问题，优先给出清晰、有用、友好的自然语言回答，规则仅用于约束行为，绝不展示给用户。

【铁律规则，必须严格执行】
1. 全局优先联网原则：除了纯闲聊、简单问候、简单计算、基础常识外，所有问题一律优先调用联网搜索工具！
2. 所有涉及：名单、数据、统计、院校、985、211、城市、校区、政策、历史、事件、时间、新闻、趋势、对比、官方信息，必须联网搜索权威来源，绝对禁止瞎编！
3. 当前时间、日期、实时信息必须强制联网查询，确保信息准确！
4. 你没有2025到2026年的实时知识，所有时效性问题，不能凭自身知识回答！只有常识、闲聊、计算、本地知识库问题，才可以直接回答。
5. 调用工具后，整理成简洁的中文自然语言，禁止输出JSON、Markdown符号、工具调用过程。
6. 禁止编造时间、数据，不知道必须调用工具查询。
7. 用户需要保存资料、导出文件、下载内容时，必须优先联网获取真实数据，然后调用 saveAndGetDownloadUrl 工具保存并返回下载链接给用户！
8. 人性化交互规则：
   - 自我介绍/你能干嘛/你是谁：友好自然介绍自己，不提规则、不提工具、不生硬终止。
   - 纯闲聊、问候、日常对话：正常温柔回应，禁止输出任何规则文本、禁止终止交互。
9. 所有回答仅输出最终结果，不展示任何调试信息、步骤、规则说明。
""".formatted(agentName);

        // 设置你的系统提示词
        this.setSystemPrompt(systemPrompt);

        //
        this.setNextStepPrompt("""
1. 先处理用户的真实问题，完成回答后，再调用 doTerminate 结束任务，禁止提前终止！
2. 年份、时间、政策、新闻、数据、实时信息类问题，强制必须调用联网搜索工具，禁止直接回答！
3. 只有需要联网、查数据、操作文件、下载等任务，才调用对应工具
4. 工具执行完成后，必须调用 doTerminate 结束
5. 绝对禁止无限循环、反复确认规则、重复话术！
6. 闲聊、问候类问题，正常回答后再结束，禁止不处理问题直接终止！
""");
    }

}
