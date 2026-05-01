package com.yang.agent;

import com.yang.rag.LoveDocumentLoader;
import com.yang.tools.MyTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;


/**
 * ReAct Agent
 * ReAct = Reasoning（推理） + Acting（行动）
 * 工作流程：思考 → 行动 → 观察 → 思考 → ... → 最终答案
 */
@Slf4j
public class ReActAgent extends BaseAgent {

    private final ChatClient chatClient; // 大脑	思考和说话的核心

    private final ToolCallback[] toolCallbacks;   // 注工具箱	具体能干啥（查、算、搜）

    private final ChatMemory chatMemory;   // 新短期记忆	当下聊天的便签纸

    private final VectorStore vectorStore; // 长期知识库	永久存储的笔记本


    public ReActAgent(ChatClient chatClient,  ToolCallback[] toolCallbacks,ChatMemory chatMemory,
                      VectorStore vectorStore) {
        super("智能助手小智", "一个会思考、会用工具的 AI 助手");
        this.chatClient = chatClient;
        this.toolCallbacks = toolCallbacks;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
    }

    @Override
    public String execute(String userInput) {
        log("收到任务：" + userInput);
        log("========== Agent 开始工作 ==========");

        // ===== 新增：强制检索，先从数据库拿数据 =====
        List<Document> relatedDocs = vectorStore.similaritySearch(
                SearchRequest.builder().query(userInput).topK(5) //　支持 TopK 检索 返回前5个，覆盖面更广
                        .similarityThreshold(0.7)  // 相似度阈值过滤 　只返回真正相关
                        .build()
        );


        StringBuilder context = new StringBuilder();
        context.append("以下是知识库中可能相关的历史记录：\n\n");
        for (Document doc : relatedDocs) {
            context.append(doc.getText()).append("\n\n");
        }
        String historyContext = context.toString();

        String systemPrompt = """
                你是 %s，%s。
                你是【行业/专业角色】，拥有10年以上资深经验。
                任务：【明确要做的事】
                背景信息：【补充上下文、素材、需求背景】
       
                请按以下步骤工作 严格要求：
                1. 先理解用户的需求
                2. 如果用户的问题涉及个人信息或历史记录，请优先参考“历史记录”中的内容
                3. 如果需要其他工具（计算器、查员工、查时间），可以调用
                4. 语言：【中文/简洁专业/口语化/正式书面】
                5. 结构：分点作答、逻辑清晰、拒绝废话
                6. 字数：【不限/500字内/精简短句】
                7. 禁止：重复内容、空洞套话、过度修饰
                8. 整合结论，给出完整可行方案
                输出格式：Markdown排版，文中标点符号使用句号或者逗号代替，内容不要造假

        """.formatted(agentName, agentDescription);

        // 把检索到的历史记录拼接到用户问题前面
        String enhancedInput = historyContext + "\n用户当前问题：" + userInput;

        // 调用 AI（带工具支持）
        String answer = chatClient.prompt()
                .system(systemPrompt)  // 顶层命令
                .user(enhancedInput)        // 用户问题+查询历史
                .toolCallbacks(toolCallbacks)   // 注册工具（计算器、查员工、搜索知识库、查时间）
                // 关键修改：使用 Builder 模式创建 MessageChatMemoryAdvisor
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build()) // 短期记忆
                .call()
                .content();

        // ===== 新增：把本次对话存入知识库 =====
        // 存入知识库
        String newKnowledge = """
        用户问题：%s
        智能助手回答：%s
        """.formatted(userInput, answer);
        Document newDoc = new Document(newKnowledge);
        vectorStore.add(List.of(newDoc));   //  把对话精华库 存入 PgVector 向量库

        log("本次对话已存入知识库");
        log("========== Agent 结束工作 ==========");
        return answer;
    }

}