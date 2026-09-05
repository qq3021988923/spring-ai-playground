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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 恋爱顾问"知心"服务：RAG 多Query 检索 + 工具调用 + 多轮对话记忆。
 *
 * <p><b>记忆分层（成本优化后的架构）：</b>
 * <ul>
 *   <li>.kryo（FileBasedChatMemory）＝短期工作记忆：最近 N 条原文直接喂主模型，保证当场对话连贯；
 *       条数由 yml love.chat-memory.max-recent-messages 控制（默认 30），本服务自建实例，独立于超级智能体。</li>
 *   <li>PgVector ＝长期记忆，按 userId 隔离（chatId），含三类文档：
 *       userId=system 知识库（LoveDocumentLoader 加载）、type=conversation 历史对话原文、type=user_profile 用户档案卡。</li>
 * </ul>
 *
 * <p><b>入库/检索三条规则（详见 saveToVectorStore 与 buildMultiQueryRagAdvisor）：</b>
 * <ol>
 *   <li>闲聊/问候轮次不存库（isPureChitChat），避免垃圾撑大长期库、污染检索；</li>
 *   <li>检索时跳过最近 RECENT_CONVERSATION_IGNORE_HOURS 小时内的对话原文（isRecentConversationDoc），
 *       近期内容 .kryo 已覆盖，防止同一份喂两遍；</li>
 *   <li>有价值的对话攒够 PROFILE_FLUSH_THRESHOLD 轮后，后台一次性提炼成"用户档案卡"入库（collectProfileRound →
 *       distillProfileAsync），跨天回来能靠档案卡回忆用户情况，成本摊薄到多轮。</li>
 * </ol>
 */
@Slf4j
@Service
public class LoveAdvisorService {

    private final VectorStore vectorStore;
    private final LoveDocumentLoader documentLoader;
    private final QueryRewriter queryRewriter;
    private final QueryExpander queryExpander;
    private final ToolCallback[] allTools;
    private final ChatClient chatClient;
    private final KeywordEnricher keywordEnricher;   // 已注释调用点，保留注入便于日后恢复关键词生成
    private final ChatMemory chatMemory;  // 持有引用，resetMyCache 清缓存时用
    private final ChatClient flashChatClient;   // 第3步档案提炼用的轻量 Flash 客户端（AgentConfig.flashChatClient Bean）

    // ==================== 第1步：闲聊过滤 ====================
    // 问候/道谢/附和等"无信息量"轮次不进 PG 长期库（kryo 短期记忆已够用），避免垃圾撑大库、污染检索
    private static final Set<String> CHIT_CHAT_KEYWORDS = Set.of(
            "你好", "您好", "你好呀", "嗨", "hello", "hi", "在吗", "在不在",
            "谢谢", "感谢", "辛苦", "嗯嗯", "哦哦", "好的", "好嘞", "好呀",
            "哈哈", "嘿嘿", "嘻嘻", "再见", "拜拜", "晚安",
            "早上好", "中午好", "下午好", "晚上好",
            "没问题", "知道了", "收到", "没事", "加油"
    );

    // ==================== 第2步：检索避重 ====================
    // PG 检索时跳过"最近 N 小时内刚存"的对话原文——近期内容 .kryo（最近30条消息）已覆盖，避免同一份喂两遍
    private static final int RECENT_CONVERSATION_IGNORE_HOURS = 12;   // 可调：嫌重复喂就调大；想"当天对话立刻能被回忆"就调小
    private static final java.time.format.DateTimeFormatter CONV_TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== 第3步：用户档案提炼（攒批低频） ====================
    // 攒够 N 轮"有价值对话"才提炼一次档案卡（type=user_profile），把每次 Flash 成本摊薄到多轮，且用户无感（后台异步）
    private static final int PROFILE_FLUSH_THRESHOLD = 5;   // 可调：越大越省 Flash、档案越"粗"；越小记得越细、略费
    private static final String PROFILE_DISTILL_PROMPT = """
            你是恋爱顾问"知心"的档案记录员。把下面一组咨询对话浓缩成用户的长期档案要点。
            规则：
            1. 只保留关于用户本人的新情况/状态/偏好/关键事件，以及知心给过的关键建议。
            2. 每条一行，用"- "开头，一句话写完（20字内），不要序号、不要客套话。
            3. 对话中没有新信息的就不用写。
            现在请处理：
            """;
    // 每个用户待提炼的对话轮次缓冲：chatId → 轮次文本（攒到阈值后取走并异步提炼）
    private final Map<String, List<String>> pendingProfileRounds = new ConcurrentHashMap<>();

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
                              KeywordEnricher keywordEnricher,
                              @Qualifier("flashChatClient") ChatClient flashChatClient,
                              @Value("${love.chat-memory.max-recent-messages:30}") int maxRecentMessages) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
        this.queryExpander = queryExpander;
        this.allTools = allTools;
        this.keywordEnricher = keywordEnricher;   // 保留注入，关键词生成调用被注释时可随时放开恢复
        this.flashChatClient = flashChatClient;   // 第3步档案提炼用（攒批异步，不阻塞对话）

        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        // 恋爱独立记忆窗口（yml: love.chat-memory.max-recent-messages，默认 30）
        // 每轮只把最近 N 条原文喂给主模型 → 主回答 token 大头在此；.kryo 文件里全量历史照存，仅返回时截取
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir, maxRecentMessages);
        this.chatMemory = chatMemory;   // 存起来，resetMyCache 清缓存时用

        // chatClientBuilder = @Primary = openAiChatModel = DeepSeek V4 Flash（当前主模型，Pro 成本高已停用）
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
        // 第2步计数：用 int[] 而非 int，是因为下面在 lambda/循环内要累加（Java 要求被 lambda 捕获的变量须为 final）
        int[] skippedRecent = {0};   // 记录因"近期对话已被 .kryo 覆盖"而跳过的轮次数

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
                    // 第2步：近期对话原文 .kryo（最近30条消息）已经覆盖，跳过避免同一份喂两遍
                    if (isRecentConversationDoc(doc)) {
                        skippedRecent[0]++;
                        continue;
                    }
                    String docId = doc.getId();
                    if (docId != null && seenIds.add(docId)) {
                        mergedDocs.add(doc);
                    }
                }
            } catch (Exception e) {
                log.warn("多Query并行检索：某个变体查询超时或失败，跳过该变体：{}", e.getMessage());
            }
        }


        log.info("【多Query检索结果变体】：{} 个，去重合并：{} 条文档，跳过近期对话：{} 条",
                expandedQueries.size(), mergedDocs.size(), skippedRecent[0]);

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

    /**
     * 把一轮问答写入 PgVector 长期库（每次对话完成后调用，由 chat/chatStream/chatStreamWithMultiQuery/chatWithTools 触发）。
     * <p>入库规则（从上到下依次过滤）：
     * 1) 回答太短（&lt;10字）→ 跳过；2) 兜底文案 → 跳过；3) 闲聊/问候轮（第1步 isPureChitChat）→ 跳过；
     * 4) 通过过滤的轮次：先收进档案攒批缓冲（第3步，攒满才提炼档案卡），再把"用户问题+回答"原文异步入库。
     * <p>说明：原文(type=conversation)仍会入库，是因为 .kryo 只覆盖最近 N 条消息，超过该窗口的更早对话要靠 PG 检索召回；
     * 检索侧已通过 isRecentConversationDoc 跳过"近期刚存"的原文，避免与 .kryo 重复喂。
     */
    private void saveToVectorStore(String question, String answer, String chatId) {
        if (answer.length() < 10) {
            log.info("回答太短({}字)，跳过存入向量库", answer.length());
            return;
        }

        if (answer.contains("抱歉，我只能回答恋爱相关的问题")) {
            log.info("兜底回复，跳过存入向量库");
            return;
        }

        // 第1步：纯问候/道谢/附和等无信息量轮次不进 PG 长期库（避免垃圾入库、撑大库、污染检索）
        if (isPureChitChat(question)) {
            log.info("闲聊/问候轮次，跳过存入长期库：{}", question);
            return;
        }

        // 第3步：这一轮值得长期记住 → 先收进"档案攒批缓冲"，攒够阈值后由后台一次性提炼成用户档案卡入库
        collectProfileRound(chatId, question, answer);

        // —— 以下为"原文入库"：每轮原文 = 一条 conversation 文档，供"超过 .kryo 窗口"的早期对话做相似度召回 ——
        String newKnowledge = """
        用户问题：%s
        恋爱顾问回答：%s
        """.formatted(question, answer);
        Document doc = new Document(newKnowledge);
        doc.getMetadata().put("userId", chatId);         // 用户隔离：检索时 WHERE userId IN (system, 该chatId)
        doc.getMetadata().put("type", "conversation");   // 文档类型：conversation=对话原文（检索避重只针对它）
        // 入库时间（yyyy-MM-dd HH:mm:ss）：第2步 isRecentConversationDoc 靠它判断"近期原文"并跳过
        doc.getMetadata().put("对话时间", java.time.LocalDateTime.now().format(CONV_TIME_FMT));
        // 异步执行：向量化 + 写入 PgVector，不阻塞 SSE 完成回调
        // 关键词生成（keywordEnricher）已注释：省 1 次 Flash/对话（纯后台零感知）。日后要恢复关键词入库，放开下面两行注释即可
        CompletableFuture.runAsync(() -> {    // 后台线程执行，主流程不等
            try {
//                Document enriched = keywordEnricher.enrich(List.of(doc)).get(0); // LLM 生成关键词（注释中，需要时恢复）
//                vectorStore.add(List.of(enriched));   // 带关键词入库（注释中，需要时恢复）
                vectorStore.add(List.of(doc));    // 原文直接入库（embedding 由 qwen text-embedding-v3 完成）
                log.info("【向量库存储】用户：{} | 问答已写入 PgVector", chatId);
            } catch (Exception e) {
                // 入库失败不影响本次对话，只记日志
                log.warn("向量库存储失败，本次问答跳过入库：{}", e.getMessage());
            }
        });  // 用 ForkJoinPool 默认线程池即可，vectorStore.add 是网络 I/O 等待，不占 CPU
    }

    // ==================== 第1步：闲聊/问候判断（零成本字符串判断，不调 LLM） ====================
    /**
     * 判断一轮对话是否纯闲聊/问候（决定是否写入长期库）。
     * 算法：问题去标点后若超过 6 字 → 默认有实质内容放行；否则看是否命中闲聊词表。
     * 允许少量误伤（如"你好漂亮"会被当成问候跳过入库）：只影响"不存库"，不影响当场聊天与 .kryo 记忆，可接受。
     */
    private boolean isPureChitChat(String question) {
        if (question == null) return true;
        String compact = question.trim().toLowerCase()
                .replaceAll("[\\s，。！？!?~～,.、；;：:\"'“”‘’…\\-]", "");
        if (compact.length() > 6) return false;   // 问题较长 → 默认有实质内容，不拦
        for (String kw : CHIT_CHAT_KEYWORDS) {
            if (compact.contains(kw)) return true;
        }
        return false;
    }

    // ==================== 第3步：档案攒批 + 异步提炼 ====================
    /**
     * 把值得记住的一轮对话收进用户缓冲；攒够 PROFILE_FLUSH_THRESHOLD 轮 → 整体取走，后台提炼成档案卡入库。
     * 说明：缓冲在内存里，若用户没攒满阈值服务就重启，这几轮不会补提炼（代价极小，原文库中仍在）。
     */
    private void collectProfileRound(String chatId, String question, String answer) {
        List<String> rounds = pendingProfileRounds.computeIfAbsent(chatId, k -> new ArrayList<>());
        List<String> toDistill;
        synchronized (rounds) {
            rounds.add("用户：" + question + "\n知心：" + answer);
            if (rounds.size() < PROFILE_FLUSH_THRESHOLD) {
                return;   // 没攒够，先放着（不花钱）
            }
            // 攒够了：整批取走再处理，防止重复提炼
            toDistill = new ArrayList<>(rounds);
            rounds.clear();
        }
        distillProfileAsync(chatId, toDistill);
    }

    /**
     * 后台异步：调 Flash 把这批轮次浓缩成"用户档案要点"，存为 type=user_profile 文档（不阻塞对话）。
     * 成本说明：每攒满 PROFILE_FLUSH_THRESHOLD 轮才调 1 次 Flash + 1 次 embedding（摊薄到多轮），
     * 比"每轮都做后处理"更省，且发生在后台，用户无感。
     */
    private void distillProfileAsync(String chatId, List<String> rounds) {
        CompletableFuture.runAsync(() -> {
            try {
                String distillText = String.join("\n", rounds);
                String profile = flashChatClient.prompt()
                        .user(PROFILE_DISTILL_PROMPT + distillText)
                        .call()
                        .content();
                if (profile == null || profile.isBlank()) {
                    log.warn("档案提炼返回空，用户 {} 的 {} 轮暂未沉淀", chatId, rounds.size());
                    return;
                }
                String timeNow = java.time.LocalDateTime.now().format(CONV_TIME_FMT);
                Document profileDoc = new Document("用户情况档案（" + timeNow + "）：\n" + profile.trim());
                profileDoc.getMetadata().put("userId", chatId);
                profileDoc.getMetadata().put("type", "user_profile");
                profileDoc.getMetadata().put("对话时间", timeNow);
                vectorStore.add(List.of(profileDoc));
                log.info("【档案卡已沉淀】用户：{} | 提炼 {} 轮 → {}", chatId, rounds.size(), profile.trim().replace('\n', ' '));
            } catch (Exception e) {
                // 提炼失败不影响对话，仅记日志（这批轮次放弃，不重试：档案是"锦上添花"，重试会重复烧 Flash）
                log.warn("档案提炼失败，用户 {} 的 {} 轮本次跳过：{}", chatId, rounds.size(), e.getMessage());
            }
        });
    }

    // ==================== 第2步：检索避重（近期原文由 .kryo 覆盖，PG 检索跳过，避免同一份喂两遍） ====================
    private boolean isRecentConversationDoc(Document doc) {
        // 只拦"对话原文"轮次；知识库(userId=system 无 type) 和档案卡(type=user_profile) 不拦
        if (!"conversation".equals(doc.getMetadata().get("type"))) return false;
        Object time = doc.getMetadata().get("对话时间");
        if (time == null) return false;
        try {
            java.time.LocalDateTime savedAt = java.time.LocalDateTime.parse(time.toString(), CONV_TIME_FMT);
            return savedAt.isAfter(java.time.LocalDateTime.now().minusHours(RECENT_CONVERSATION_IGNORE_HOURS));
        } catch (Exception e) {
            return false;   // 时间解析失败不拦，宁多不漏
        }
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
