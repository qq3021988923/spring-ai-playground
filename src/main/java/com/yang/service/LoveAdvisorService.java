package com.yang.service;

import com.yang.advisor.MyLoggerAdvisor;
import com.yang.advisor.ReReadingAdvisor;
import com.yang.chatmemory.FileBasedChatMemory;
import com.yang.rag.KeywordEnricher;
import com.yang.rag.LoveDocumentLoader;
import com.yang.rag.QueryExpander;
import com.yang.model.dto.LoveReport;
import com.yang.rag.QueryRewriter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LoveAdvisorService {

    private final VectorStore vectorStore;
    private final LoveDocumentLoader documentLoader;
    private final QueryRewriter queryRewriter;
    private final QueryExpander queryExpander;
    private final ToolCallback[] allTools;
    private final ChatClient chatClient;
    private final KeywordEnricher keywordEnricher;
    private final ChatMemory chatMemory;  // 持有引用，resetMyCache 清缓存时用

    private static final String SYSTEM_PROMPT = """
            你是一位专业的恋爱顾问，名叫"知心"，温柔体贴、善解人意，口语化表达，每条回复最多用1个emoji。
            1. 涉及实时信息、时效性内容（新闻、天气、明星动态），必须调用 searchWeb 搜索，关键词加当前年份，禁止用自身知识回答时效性问题。
            2. 用户要求图片时，调用 searchImage 搜索。
            3. 用户提供网址或需要网页详情时，调用 scrapeWebPage 抓取。
            4. 查不到的信息就告知"目前无法获取该信息"，禁止编造。
            5. 结合知识库内容回答；知识库没有的，用专业知识补充。
            6. 先简洁回答核心问题，结尾问"需要我给你整理一份详细的行动方案吗？"。
            7. 结尾问"需要我举个实际的小案例吗？"，用户确认后再给案例。
            8. 禁止重复内容、空洞套话、emoji堆砌。
            """;

    public LoveAdvisorService(ChatClient.Builder chatClientBuilder,
                              LoveDocumentLoader documentLoader,
                              VectorStore vectorStore,
                              QueryRewriter queryRewriter,
                              QueryExpander queryExpander,
                              ToolCallback[] allTools,
                              KeywordEnricher keywordEnricher) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
        this.queryExpander = queryExpander;
        this.allTools = allTools;
        this.keywordEnricher=keywordEnricher;

        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir, 100);
        this.chatMemory = chatMemory;   // 存起来，resetMyCache 清缓存时用

        // chatClientBuilder = @Primary = openAiChatModel = DeepSeek V3（当前主模型）
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                       // new ReReadingAdvisor(), // 重复用户的问题两遍
                        new MyLoggerAdvisor()
                )
                .build();
    }

    // ==================== 公开方法 ====================

    public String chat(String userQuestion, String chatId, String status) {
        log.info("用户问题：{}，会话ID：{}，状态：{}", userQuestion, chatId, status);
        String rewrittenQuery = rewriteIfNeeded(userQuestion);
        String answer = chatClient
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(buildRagAdvisor(status, chatId))
                .call()
                .content();
        log.info("回复：{}", answer);
        saveToVectorStore(userQuestion, answer, chatId);
        return answer;
    }

    /** 流式对话：单 Query RAG + 工具调用 */
    public Flux<String> chatStream(String userQuestion, String chatId, String status) {
        log.info("用户问题（流式）：{}，会话ID：{}，状态：{}", userQuestion, chatId, status);
        String rewrittenQuery = rewriteIfNeeded(userQuestion);
        StringBuilder fullAnswer = new StringBuilder();
        return chatClient
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(buildRagAdvisor(status, chatId))
                .toolCallbacks(allTools)
                .stream()
                .content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> {
                    saveToVectorStore(userQuestion, fullAnswer.toString(), chatId);
                    log.info("流式对话已存入向量库");
                });
    }

    /** 流式对话：多 Query 扩展 RAG + 工具调用 */
    public Flux<String> chatStreamWithMultiQuery(String userQuestion, String chatId, String status) {
        log.info("【用户原始问题】：{}，【用户ID】：{}，【状态】：{}", userQuestion, chatId, status);

        // 润色 将用户问题改成ai听得懂的话！ 让后面的的检索和LLM更好理解
        String rewrittenQuery = rewriteIfNeeded(userQuestion);

        // 拼完整回答用的桶（流式一边出字一边往里装）
        StringBuilder fullAnswer = new StringBuilder();

        return chatClient
                .prompt()
                .user(rewrittenQuery) // 把用户问题塞进去
                .advisors(spec -> spec.param(   // 第一个Advisor：对话记忆
                        ChatMemory.CONVERSATION_ID, chatId)) // 根据 chatId 从 .kryo 文件加载历史对话

                .advisors(buildMultiQueryRagAdvisor( // 第二个 Advisor 检索
                        rewrittenQuery, status, chatId)) // 多Query扩展 -> Pgvector → 去重 → 注入上下文
                .toolCallbacks(allTools)
                .stream()                               // 流式调用 LLM
                .content()                                // 只要 LLM 回复的纯文本
                .doOnNext(fullAnswer::append) // 每收到一个字就拼到 fullAnswer 里
                .doOnComplete(() -> {           // LLM 说完了
                    saveToVectorStore(          // 存到 PgVector（长期记忆）
                            userQuestion,       // 存的是用户原始问题
                            fullAnswer.toString(),  // 和完整 AI 回答
                            chatId);               // 打上当前用户标签
                });
    }

    /**
     * 结构化输出：多 Query RAG + 工具调用 → 返回 JSON 恋爱报告
     *
     * <p><b>与流式接口的区别：</b></p>
     * - 流式：一个字一个字推给前端，用于聊天窗口打字机效果
     * - 本方法：大模型回答完后，Spring AI 把 JSON 自动转成 LoveReport 对象返回
     *
     * <p><b>面试要点：</b></p>
     * "通过 ChatClient.call().entity() 约束大模型按 JSON Schema 输出，
     *  实现了大模型 → 结构化 API 的转换，可用于后端对接、前端分块渲染等场景。"
     */
    public LoveReport chatWithStructuredReport(String userQuestion, String chatId, String status) {
        log.info("用户问题（结构化报告）：{}，会话ID：{}", userQuestion, chatId);
        String rewrittenQuery = rewriteIfNeeded(userQuestion);

        /*
         * .call().entity(LoveReport.class) 做了什么：
         * 1. Spring AI 自动读取 LoveReport 的字段（problem、analysis等）
         * 2. 告诉大模型："请按这个 JSON 结构输出，不要输出废话"
         * 3. 大模型返回 JSON 字符串
         * 4. Spring AI 反序列化成 LoveReport 对象
         * 5. 如果格式不对，自动让大模型重试一次
         */
        LoveReport report = chatClient
                .prompt()
                .user(rewrittenQuery
                        + "\n\n请以JSON格式输出一份完整的恋爱分析报告，必须包含：问题诊断、深度分析、建议清单、行动计划、风险等级和鼓励语。不要输出JSON以外的内容。")
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(buildMultiQueryRagAdvisor(rewrittenQuery, status, chatId))
                .toolCallbacks(allTools)
                .call()
                .entity(LoveReport.class);

        log.info("结构化报告生成成功：{}", report.getProblem());
        return report;
    }

    public String chatWithTools(String userQuestion, String chatId, String status) {
        log.info("用户问题（工具调用）：{}，会话ID：{}，状态：{}", userQuestion, chatId, status);
        String rewrittenQuery = rewriteIfNeeded(userQuestion);
        String answer = chatClient
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(buildRagAdvisor(status, chatId))
                .toolCallbacks(allTools)
                .call()
                .content();
        log.info("回复：{}", answer);
        saveToVectorStore(userQuestion, answer, chatId);
        return answer;
    }

    // ==================== 私有方法 ====================


    private String rewriteIfNeeded(String userQuestion) {
        if (userQuestion.length() < 15) {
            String rewritten = queryRewriter.doQueryRewrite(userQuestion);
            log.info("【AI润色用户对话】：{}", rewritten);
            return rewritten;
        }
        return userQuestion;
    }

    /** 标准 RAG Advisor（单 Query + 用户隔离） */
    private Advisor buildRagAdvisor(String status, String chatId) {
        VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.5)
                .topK(3);

        // 用户隔离：知识库(userId=system) + 当前用户对话
        retrieverBuilder.filterExpression(
                new FilterExpressionBuilder().in("userId", "system", chatId).build()
        );

        if (status != null && !status.isEmpty()) {
            log.info("RAG 状态过滤：{}", status);
        }

        DocumentRetriever retriever = retrieverBuilder.build();
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(augmenter)
                .build();
    }

    /** 多 Query 扩展 RAG Advisor（+ 用户隔离） */
    private Advisor buildMultiQueryRagAdvisor(String rewrittenQuery, String status, String chatId) {

        // ① 调 DeepSeek 把 1 个问题变成 2 个不同角度
        //    "(已经是润色之后的用户问题)追求心仪对象的方法" → 变体1（原始） + 变体2（扩展）
        List<Query> expandedQueries = queryExpander.expand(rewrittenQuery); // 输出 2 个变体：
        log.info("【润色+变体】：{}", expandedQueries);

        // ② 准备两个容器：Set 去重用，List 收结果
        Set<String> seenIds = new LinkedHashSet<>(); // 记录已见过的文档 ID
        List<Document> mergedDocs = new ArrayList<>(); // 存放去重后的文档

        // 用户隔离过滤器：知识库(userId=system) + 当前用户对话,只查询用户相关的数据+恋爱文档的相关数据
        Filter.Expression userFilter = new FilterExpressionBuilder()
                .in("userId", "system", chatId).build(); //查 PgVector 时加一个 WHERE  IN ("system", "love_u_xxx")

        // 并行检索：2个变体同时查 PgVector，减少延迟
        // CompletableFuture 异步发起所有查询，再统一收集结果
        List<CompletableFuture<List<Document>>> futures = expandedQueries.stream()
                .map(q -> CompletableFuture.supplyAsync(() -> { //   开线程，主线程不等

                    SearchRequest searchRequest = SearchRequest.builder()
                            .query(q.text())
                            .topK(3)
                            .similarityThreshold(0.6)
                            .filterExpression(userFilter)
                            .build();
                    log.info("【变体循环打印】：{}", q.text());

                    return vectorStore.similaritySearch(searchRequest);

                }))
                .toList();

        // 等待所有查询完成，按 docId 去重合并（get 顺序等，但查询本身是并发的）
        for (CompletableFuture<List<Document>> future : futures) {
            try {
                List<Document> docs = future.get(5, TimeUnit.SECONDS); // 5秒超时保护
                for (Document doc : docs) {
                    String docId = doc.getId();
                    if (docId != null && seenIds.add(docId)) {
                        mergedDocs.add(doc);
                    }
                }
            } catch (Exception e) {
                log.warn("多Query并行检索：某个变体查询超时或失败，跳过该变体：{}", e.getMessage());
            }
        }


        log.info("【多Query检索结果变体】：{} 个，去重合并：{} 条文档", expandedQueries.size(), mergedDocs.size());

        // 搜到文档或没搜到，都构建合法的 Advisor
        // allowEmptyContext(true)：上下文为空时不注入"请说你不知道"的指令，让 AI 自行判断
        DocumentRetriever mergedRetriever = query -> mergedDocs;
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(mergedRetriever)
                .queryAugmenter(augmenter)
                .build();
    }

    private void saveToVectorStore(String question, String answer, String chatId) {
        if (answer.length() < 10) {
            log.info("回答太短({}字)，跳过存入向量库", answer.length());
            return;
        }

        if (answer.contains("抱歉，我只能回答恋爱相关的问题")) {
            log.info("兜底回复，跳过存入向量库");
            return;
        }

        String newKnowledge = """
        用户问题：%s
        恋爱顾问回答：%s
        """.formatted(question, answer);
        Document doc = new Document(newKnowledge);
        doc.getMetadata().put("userId", chatId);
        doc.getMetadata().put("type", "conversation");
        // 年月日 时分秒
        doc.getMetadata().put("对话时间", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        // 异步执行：关键词生成（LLM调用） + 向量库存储，不阻塞 SSE 完成回调
        CompletableFuture.runAsync(() -> {    // "去后台跑这段代码，主线程不用等"
            try {
                Document enriched = keywordEnricher.enrich(List.of(doc)).get(0); // LLM 生成关键词
                vectorStore.add(List.of(enriched));
                log.info("【向量库存储，+关键字】用户：{} | 问答已写入 PgVector", chatId);
            } catch (Exception e) {
                // 关键词生成失败不阻塞，直接用原文存储
                log.warn("关键词生成失败，降级存储原文：{}", e.getMessage());
                vectorStore.add(List.of(doc));    // 失败了降级存原文
            }
        });  // 用 ForkJoinPool 默认线程池即可，关键词生成是 I/O 等待，不占 CPU
    }

    // ===== 用户级：只清自己的 =====
    public String resetMyCache(String userId) {
        // 1. 通过 chatMemory.clear() 清缓存 + 删文件（不走直接 File.delete，保证缓存同步）
        String chatId = "love_" + userId;
        chatMemory.clear(chatId);   // 清 ConcurrentHashMap 缓存 + 删 .kryo 文件
        int deletedFiles = 1;
        // 2. 只删当前用户的对话记录（metadata.userId 匹配的），不动知识库文档
        List<String> idsToDelete = new ArrayList<>();
        vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query("").topK(10000).build()
        ).forEach(doc -> {
            String uid = (String) doc.getMetadata().get("userId");
            if (userId.equals(uid) || ("love_" + userId).equals(uid)) {
                String id = doc.getId();
                if (id != null) idsToDelete.add(id);
            }
        });
        if (!idsToDelete.isEmpty()) {
            vectorStore.delete(idsToDelete);
        }
        log.info("用户 {} 缓存已清空：{} 个文件，{} 条对话记录", userId, deletedFiles, idsToDelete.size());
        return "缓存已清空：" + deletedFiles + " 个记忆文件，" + idsToDelete.size() + " 条对话记录";
    }

    // ===== 系统级：清空一切 + 重载知识库 =====
    public String resetAllSystem() {
        // 1. 清空所有文件记忆（通过 clearAll 同时清缓存 + 删文件）
        int deletedFiles = ((FileBasedChatMemory) chatMemory).clearAll();
        // 2. 清空整个向量库 + 重新加载知识库
        documentLoader.clearKnowledgeBase();
        documentLoader.initKnowledgeBase();
        log.info("系统已重置：{} 个文件，向量库已清空并重新加载", deletedFiles);
        return "系统已重置：清空 " + deletedFiles + " 个记忆文件，数据库已清空，知识库已重新加载";
    }

    // 启动时自动加载知识库（只执行一次），后续 "清空缓存" 只删个人数据不动知识库
//    @jakarta.annotation.PostConstruct
//    public void init() {
//        documentLoader.initKnowledgeBase();
//    }
}
