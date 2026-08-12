package com.yang.agent;

import com.yang.advisor.MyLoggerAdvisor;
import com.yang.advisor.ReReadingAdvisor;
import com.yang.rag.QueryExpander;
import com.yang.rag.QueryRewriter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * AI 超级智能体 — 参考鱼皮项目架构：简洁 prompt + 每次请求新建实例
 */
public class YangManus extends ToolCallAgent {

    public YangManus(ToolCallback[] allTools,
                     ChatModel chatModel,          // 当前为 DeepSeek V3（由 Controller 注入 openAiChatModel）
                     ChatMemory chatMemory,
                     VectorStore vectorStore,
                     QueryExpander queryExpander,
                     QueryRewriter queryRewriter) {
        super(allTools);
        this.setName("小羊~");
        this.setMaxSteps(18);
        this.setChatMemory(chatMemory);
        this.setVectorStore(vectorStore);
        this.setQueryExpander(queryExpander);
        this.setQueryRewriter(queryRewriter);

        // 参考鱼皮项目：简洁 System Prompt，聚焦工具调用
        // 动态注入当前日期，解决搜索返回旧数据的问题
        String today = java.time.LocalDate.now().toString();
        int year = java.time.Year.now().getValue();
//        this.setSystemPrompt(String.format("""
//                你是 小羊~，一个全能型 AI 助手，旨在解决用户提出的任何任务。
//                你拥有多种工具可以调用来高效完成复杂的请求。
//                【当前日期】%s
//                【重要规则】
//                1. 绝对不能使用内置搜索功能。
//                2. 所有实时信息、最新数据、近期新闻、天气、价格、赛事结果等时效性内容，必须通过调用 searchWeb 工具来获取。
//                3. 搜索时效性内容时，必须在搜索关键词里加上当前年份（%d），否则搜索引擎会返回过时数据。
//                4. 没有调用工具就回答时效性问题，回答就是错误的，会给用户带来误导。
//                5. 绝对禁止编造任何你不知道的信息，不知道就调用工具查询。
//                """, today, year));

        this.setSystemPrompt(String.format("""
        你是小羊，全能AI助手。用户用什么语言问，你就用什么语言答。
        【当前日期】%s
        1. 所有时效性数据必须通过 searchWeb 查询，关键词含当前年份（%d）。
        2. 查不到数据就告知用户"目前无法获取该信息"，禁止推断。
        3. 每次搜索完立刻整理结果并 doTerminate，最多搜索 1 次。
        """, today, year));


//        this.setNextStepPrompt("""
//                根据用户的需求，主动选择最合适的工具或工具组合。
//                每次使用完工具后，立刻整理结果回复用户，然后调用 doTerminate 结束！
//                查询时间/天气等实时数据：先searchWeb，若结果无具体数值，立刻scrapeWebPage抓取链接获取详情。
//                禁止反复搜索同一个问题！
//                """);

        this.setNextStepPrompt("""
        搜索 → 有结果就整理回答 → 调用doTerminate结束。
        没结果就scrapeWebPage抓取补充，禁止反复搜索同一问题。
        """);

        ChatClient chatClient = ChatClient.builder(chatModel)   // DeepSeek V3（当前主模型）
                .defaultAdvisors(new ReReadingAdvisor(), new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
