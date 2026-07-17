package com.yang.service;

import com.yang.advisor.MyLoggerAdvisor;
import com.yang.advisor.ReReadingAdvisor;
import com.yang.chatmemory.FileBasedChatMemory;
import com.yang.rag.LoveDocumentLoader;
import com.yang.rag.QueryExpander;
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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

@Slf4j
@Service
public class LoveAdvisorService {

    private final VectorStore vectorStore;
    private final LoveDocumentLoader documentLoader;
    private final QueryRewriter queryRewriter;
    private final QueryExpander queryExpander;
    private final ToolCallback[] allTools;
    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            你是一位专业的恋爱顾问，名叫"小红娘"。
            你的特点：
            1. 温柔体贴，善解人意
            2. 善于倾听，给出实用建议
            3. 语言亲切，使用 emoji 让对话更生动
            4. 结合知识库中的内容来回答
            5. 禁止：重复内容、空洞套话、过度修饰
            6. 整合结论，给出完整可行方案
            7. 最后给1个简易实操小案例
            """;

    public LoveAdvisorService(ChatClient.Builder chatClientBuilder,
                              LoveDocumentLoader documentLoader,
                              VectorStore vectorStore,
                              QueryRewriter queryRewriter,
                              QueryExpander queryExpander,
                              ToolCallback[] allTools) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
        this.queryExpander = queryExpander;
        this.allTools = allTools;

        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);

        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new ReReadingAdvisor(),
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
                .advisors(buildRagAdvisor(status))
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
                .advisors(buildRagAdvisor(status))
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
        log.info("用户问题（多Query流式）：{}，会话ID：{}，状态：{}", userQuestion, chatId, status);
        String rewrittenQuery = rewriteIfNeeded(userQuestion);
        StringBuilder fullAnswer = new StringBuilder();
        return chatClient
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(buildMultiQueryRagAdvisor(rewrittenQuery, status))
                .toolCallbacks(allTools)
                .stream()
                .content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> {
                    log.info("多Query扩展检索完成");
                    saveToVectorStore(userQuestion, fullAnswer.toString(), chatId);
                });
    }

    public String chatWithTools(String userQuestion, String chatId, String status) {
        log.info("用户问题（工具调用）：{}，会话ID：{}，状态：{}", userQuestion, chatId, status);
        String rewrittenQuery = rewriteIfNeeded(userQuestion);
        String answer = chatClient
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(buildRagAdvisor(status))
                .toolCallbacks(allTools)
                .call()
                .content();
        log.info("回复：{}", answer);
        saveToVectorStore(userQuestion, answer, chatId);
        return answer;
    }

    // ==================== 私有方法 ====================

    private String rewriteIfNeeded(String userQuestion) {
        if (userQuestion.length() < 3) {
            return queryRewriter.doQueryRewrite(userQuestion);
        }
        log.info("问题已足够具体，跳过改写");
        return userQuestion;
    }

    /** 标准 RAG Advisor（单 Query） */
    private Advisor buildRagAdvisor(String status) {
        VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.5)
                .topK(3);

        if (status != null && !status.isEmpty()) {
            Filter.Expression filter = new FilterExpressionBuilder()
                    .eq("status", status)
                    .build();
            retrieverBuilder.filterExpression(filter);
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

    /** 多 Query 扩展 RAG Advisor */
    private Advisor buildMultiQueryRagAdvisor(String rewrittenQuery, String status) {
        List<Query> expandedQueries = queryExpander.expand(rewrittenQuery); // 输出 4 个变体：

        Set<String> seenIds = new LinkedHashSet<>();
        List<Document> mergedDocs = new ArrayList<>();

        for (Query q : expandedQueries) {
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(q.text())
                    .topK(3)
                    .similarityThreshold(0.5);

            if (status != null && !status.isEmpty()) {
                Filter.Expression filter = new FilterExpressionBuilder()
                        .eq("status", status)
                        .build();
                searchBuilder.filterExpression(filter);
            }

            List<Document> docs = vectorStore.similaritySearch(searchBuilder.build());
            for (Document doc : docs) {
                String docId = doc.getId();
                if (docId != null && seenIds.add(docId)) {
                    mergedDocs.add(doc);
                }
            }
        }

        log.info("多Query检索：{} 个变体 → 去重后 {} 条文档", expandedQueries.size(), mergedDocs.size());

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
        doc.getMetadata().put("timestamp", System.currentTimeMillis());
        vectorStore.add(List.of(doc));
        log.info("高质量问答已存入向量库，用户：{}", chatId);
    }

    @PostConstruct
    public void init() {
        documentLoader.initKnowledgeBase();
    }
}
