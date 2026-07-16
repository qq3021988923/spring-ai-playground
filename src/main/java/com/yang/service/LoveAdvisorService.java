package com.yang.service;

import com.yang.advisor.MyLoggerAdvisor;
import com.yang.advisor.ReReadingAdvisor;
import com.yang.chatmemory.FileBasedChatMemory;
import com.yang.rag.LoveDocumentLoader;
import com.yang.rag.QueryRewriter;
import org.springframework.ai.tool.ToolCallback;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
public class LoveAdvisorService {

    private final VectorStore vectorStore;
    private final LoveDocumentLoader documentLoader;
    private final QueryRewriter queryRewriter;
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
                              ToolCallback[] allTools) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
        this.allTools = allTools;

        // 文件版记忆，重启不丢
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);

        // ChatClient 焊死：System Prompt + 对话记忆 + 生产级 RAG + 日志
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(), // 对话记忆
                        new ReReadingAdvisor(),                                // Re2 推理增强
                        buildRagAdvisor(),                                     // 生产级 RAG（检索+兜底）
                        new MyLoggerAdvisor()                                  // 日志
                )
                .build();
    }

    // ==================== 公开方法 ====================

    /**
     * 同步对话：Query 改写 → RAG 检索 → LLM 回答 → 存向量库
     */
    public String chat(String userQuestion, String chatId) {
        log.info("用户问题：{}，会话ID：{}", userQuestion, chatId);

        // 1. Query 改写：把口语转成检索关键词
        String rewrittenQuery = queryRewriter.doQueryRewrite(userQuestion);
        log.info("改写后查询：{}", rewrittenQuery);

        // 2. 调用 AI
        String answer = chatClient
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();

        log.info("回复：{}", answer);

        // 3. 存入 PgVector 长期记忆
        saveToVectorStore(userQuestion, answer, chatId);
        return answer;
    }

    /**
     * 流式对话（SSE）：Query 改写 → RAG 检索 → LLM 流式回答 → 流结束后存向量库
     */
    public Flux<String> chatStream(String userQuestion, String chatId) {
        log.info("用户问题（流式）：{}，会话ID：{}", userQuestion, chatId);

        // 1. Query 改写
        String rewrittenQuery = queryRewriter.doQueryRewrite(userQuestion);
        log.info("改写后查询：{}", rewrittenQuery);

        // 2. 流式调用
        StringBuilder fullAnswer = new StringBuilder();

        return chatClient
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> {
                    saveToVectorStore(userQuestion, fullAnswer.toString(), chatId);
                    log.info("流式对话已存入向量库");
                });
    }

    /**
     * 工具调用对话：AI 可以自主调用 8 个工具（搜索、爬虫、文件、图片等）完成任务
     */
    public String chatWithTools(String userQuestion, String chatId) {
        log.info("用户问题（工具调用）：{}，会话ID：{}", userQuestion, chatId);

        String rewrittenQuery = queryRewriter.doQueryRewrite(userQuestion);

        String answer = chatClient
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(allTools)    // ← 核心：挂载 8 个工具
                .call()
                .content();

        log.info("回复：{}", answer);
        saveToVectorStore(userQuestion, answer, chatId);
        return answer;
    }

    // ==================== 私有方法 ====================

    /**
     * 构建生产级 RAG Advisor：
     * - 相似度阈值 0.5（太低不相关的结果不要）
     * - TopK 3（最多返回 3 条）
     * - 翻不到资料时兜底，拒绝瞎编
     */
    private Advisor buildRagAdvisor() {
        // 检索器：控制检索参数
        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.5)   // 生产级：低于 50% 相似度的不要
                .topK(3)                    // 生产级：最多返回 3 条
                .build();

        // 兜底模板：翻不到相关资料时用这个回复，不让 AI 瞎编
        PromptTemplate fallbackTemplate = new PromptTemplate("""
                你应该输出下面的内容：
                抱歉，我只能回答恋爱相关的问题，别的没办法帮到您哦。
                """);

        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)                        // 不允许空上下文
                .emptyContextPromptTemplate(fallbackTemplate)    // 翻不到资料时的兜底回复
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(augmenter)
                .build();
    }

    /**
     * 把问答存入 PgVector 向量库（带质量过滤 + 用户标签）
     */
    private void saveToVectorStore(String question, String answer, String chatId) {
        // ===== 质量过滤 =====
        // 太短跳过（"你好""嗯""哦"没有检索价值）
        if (answer.length() < 20) {
            log.info("回答太短({}字)，跳过存入向量库", answer.length());
            return;
        }
        // 兜底回复跳过（翻不到资料时的"抱歉，我只能回答恋爱问题"）
        if (answer.contains("抱歉，我只能回答恋爱相关的问题")) {
            log.info("兜底回复，跳过存入向量库");
            return;
        }

        // ===== 存入向量库（带标签） =====
        String newKnowledge = """
        用户问题：%s
        恋爱顾问回答：%s
        """.formatted(question, answer);

        Document doc = new Document(newKnowledge);
        doc.getMetadata().put("userId", chatId);           // 用户来源
        doc.getMetadata().put("type", "conversation");     // 标记类型
        doc.getMetadata().put("timestamp", System.currentTimeMillis());

        vectorStore.add(List.of(doc));
        log.info("高质量问答已存入向量库，用户：{}", chatId);
    }

    /**
     * 启动时加载知识库文档到 PgVector
     */
    @PostConstruct
    public void init() {
        documentLoader.initKnowledgeBase();
    }
}
