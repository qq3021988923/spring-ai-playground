package com.yang.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yang.rag.QueryExpander;
import com.yang.rag.QueryRewriter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Data
@Slf4j
public abstract class BaseAgent {
    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    private AgentState state = AgentState.IDLE;
    private int currentStep = 0;
    private int maxSteps = 5;
    protected ChatClient chatClient;
    protected List<Message> messageList = new ArrayList<>();
    protected ChatMemory chatMemory;
    protected VectorStore vectorStore;
    protected QueryExpander queryExpander;
    protected QueryRewriter queryRewriter;
    protected String currentConversationId;

    private void reset(String conversationId) {
        this.currentConversationId = conversationId;
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        this.messageList.clear();
        if (chatMemory != null) {
            List<Message> history = chatMemory.get(conversationId);
            if (!history.isEmpty()) {
                messageList.addAll(history);
                log.info("【{}】加载历史对话：{} 条，会话ID：{}", name, history.size(), conversationId);
            }
        }
        log.info("【{}】状态已重置：IDLE", name);
    }

    // ===== String 版本：后台思考 → 一次性返回（跟恋爱大师的同步接口一样） =====
    public String runString(String conversationId, String userPrompt) {
        reset(conversationId);
        if (this.state != AgentState.IDLE) return "智能体状态异常";
        if (StrUtil.isBlank(userPrompt)) return "用户输入不能为空";

        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        int dynamicMaxSteps = getMaxSteps();
        String finalAnswer = "";

        for (int i = 0; i < dynamicMaxSteps && state != AgentState.FINISHED; i++) {
            currentStep = i + 1;
            log.info("执行步骤：{}/{}", currentStep, dynamicMaxSteps);
            String stepResult = step();
            if (StrUtil.isNotBlank(stepResult) && state == AgentState.FINISHED) {
                finalAnswer = stepResult;
            }
        }

        if (currentStep >= dynamicMaxSteps && state != AgentState.FINISHED) {
            state = AgentState.FINISHED;
            finalAnswer = "任务终止：已达到最大步骤数(" + dynamicMaxSteps + ")";
        }

        // 兜底
        if (StrUtil.isBlank(finalAnswer) || finalAnswer.length() <= 5) {
            finalAnswer = messageList.stream()
                    .filter(m -> m instanceof org.springframework.ai.chat.messages.AssistantMessage)
                    .map(m -> ((org.springframework.ai.chat.messages.AssistantMessage) m).getText())
                    .filter(t -> t != null && t.length() > 10)
                    .reduce((first, second) -> second)
                    .orElse("思考完成，当前对话无需调用工具");
            finalAnswer = finalAnswer.replaceAll("\\{.*?\\}", "").replace("doTerminate", "").trim();
        }

        saveConversationToVectorStore(userPrompt, finalAnswer, conversationId);
        if (chatMemory != null) {
            chatMemory.add(conversationId, new UserMessage(userPrompt));
            chatMemory.add(conversationId, new org.springframework.ai.chat.messages.AssistantMessage(finalAnswer));
        }

        this.state = AgentState.IDLE;
        this.cleanup();
        return finalAnswer;
    }

    public String run(String conversationId, String userPrompt) {
        return executeCoreLogic(conversationId, userPrompt);
    }

    // ===== 旧 SseEmitter（保留兼容） =====
    public SseEmitter runStream(String conversationId, String userPrompt) {
        reset(conversationId);
        SseEmitter sseEmitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：智能体状态异常：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：用户输入不能为空");
                    sseEmitter.complete();
                    return;
                }
                this.state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));
                String finalAnswer = "";
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    currentStep = i + 1;
                    log.info("执行步骤：{}/{}", currentStep, maxSteps);
                    String stepResult = step();
                    if (StrUtil.isNotBlank(stepResult) && state == AgentState.FINISHED) {
                        finalAnswer = stepResult;
                    }
                }
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    finalAnswer = "任务终止：已达到最大步骤数(" + maxSteps + ")";
                }
                sseEmitter.send(finalAnswer);
                saveConversationToVectorStore(userPrompt, finalAnswer, conversationId);
                if (chatMemory != null) {
                    chatMemory.add(conversationId, new UserMessage(userPrompt));
                    chatMemory.add(conversationId, new org.springframework.ai.chat.messages.AssistantMessage(finalAnswer));
                }
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("流式执行异常", e);
                try { sseEmitter.send("执行错误：" + e.getMessage()); sseEmitter.complete(); }
                catch (Exception ex) { sseEmitter.completeWithError(ex); }
            } finally { this.cleanup(); }
        });
        return sseEmitter;
    }

    // ===== 新 Flux 流式（后台思考 → 一次性输出，前端显示"搜索中..."） =====
    public Flux<String> runStreamFlux(String conversationId, String userPrompt) {
        return Flux.create(sink -> {
            reset(conversationId);
            if (this.state != AgentState.IDLE) { sink.next("智能体状态异常"); sink.complete(); return; }
            if (StrUtil.isBlank(userPrompt)) { sink.next("用户输入不能为空"); sink.complete(); return; }

            this.state = AgentState.RUNNING;
            messageList.add(new UserMessage(userPrompt));

            int dynamicMaxSteps = getMaxSteps();
            String finalAnswer = "";

            for (int i = 0; i < dynamicMaxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("执行步骤：{}/{}", currentStep, dynamicMaxSteps);
                String stepResult = step();
                if (StrUtil.isNotBlank(stepResult) && state == AgentState.FINISHED) {
                    finalAnswer = stepResult;
                }
            }

            if (currentStep >= dynamicMaxSteps && state != AgentState.FINISHED) {
                state = AgentState.FINISHED;
                finalAnswer = "任务终止：已达到最大步骤数(" + dynamicMaxSteps + ")";
            }

            // 只推最终回答，不推中间步骤
            if (StrUtil.isNotBlank(finalAnswer) && finalAnswer.length() > 5) {
                sink.next(finalAnswer);
            } else {
                String fallback = messageList.stream()
                        .filter(m -> m instanceof org.springframework.ai.chat.messages.AssistantMessage)
                        .map(m -> ((org.springframework.ai.chat.messages.AssistantMessage) m).getText())
                        .filter(t -> t != null && t.length() > 10)
                        .reduce((first, second) -> second)
                        .orElse("思考完成");
                String clean = fallback.replaceAll("\\{.*?\\}", "").replace("doTerminate", "").trim();
                sink.next(StrUtil.isNotBlank(clean) ? clean : "思考完成");
            }

            saveConversationToVectorStore(userPrompt, finalAnswer, conversationId);
            if (chatMemory != null) {
                chatMemory.add(conversationId, new UserMessage(userPrompt));
                chatMemory.add(conversationId, new org.springframework.ai.chat.messages.AssistantMessage(finalAnswer));
            }

            sink.next("[DONE]");
            sink.complete();
            this.state = AgentState.IDLE;
            this.cleanup();
        });
    }

    private String executeCoreLogic(String conversationId, String userPrompt) {
        reset(conversationId);
        if (this.state != AgentState.IDLE) { throw new RuntimeException("智能体状态异常：" + this.state); }
        if (StrUtil.isBlank(userPrompt)) { throw new RuntimeException("用户输入不能为空"); }
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        addRagContextToMessageList(userPrompt);
        String finalAnswer = "";
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("执行步骤：{}/{}", currentStep, maxSteps);
                String stepResult = step();
                if (StrUtil.isNotBlank(stepResult) && state == AgentState.FINISHED) {
                    finalAnswer = stepResult;
                }
            }
            if (currentStep >= maxSteps) { state = AgentState.FINISHED; finalAnswer = "任务终止：已达到最大步骤数(" + maxSteps + ")"; }
            saveConversationToVectorStore(userPrompt, finalAnswer, conversationId);
            if (chatMemory != null) {
                chatMemory.add(conversationId, new UserMessage(userPrompt));
                chatMemory.add(conversationId, new org.springframework.ai.chat.messages.AssistantMessage(finalAnswer));
            }
            return finalAnswer;
        } catch (Exception e) { state = AgentState.ERROR; log.error("智能体执行异常", e); return "执行错误：" + e.getMessage(); }
        finally { this.cleanup(); }
    }

    protected void saveConversationToVectorStore(String userQuestion, String agentAnswer, String conversationId) {
        if (vectorStore == null) return;
        if (agentAnswer == null || agentAnswer.length() < 10) return;
        if (agentAnswer.contains("抱歉") || agentAnswer.contains("我只能回答")) return;
        try {
            String content = "用户问题：" + userQuestion + "\nAI回答：" + agentAnswer;
            Document document = new Document(content);
            document.getMetadata().put("userId", conversationId);
            document.getMetadata().put("type", "conversation");
            document.getMetadata().put("对话时间", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            vectorStore.add(List.of(document));
            log.info("✅ 【{}】高质量问答已存入向量库", name);
        } catch (Exception e) { log.error("❌ 【{}】向量库存储失败", name, e); }
    }

    private void addRagContextToMessageList(String userPrompt) {
        if (vectorStore == null) return;
        try {
            String queryText = userPrompt;
            if (queryRewriter != null && userPrompt.length() < 8) {
                queryText = queryRewriter.doQueryRewrite(userPrompt);
            }
            // 用户隔离：知识库(userId=system) + 当前用户对话
            String convId = currentConversationId != null ? currentConversationId : "";
            Filter.Expression userFilter = new FilterExpressionBuilder().in("userId", "system", convId).build();
            List<Document> allDocs = new ArrayList<>();
            if (queryExpander != null) {
                List<Query> expandedQueries = queryExpander.expand(queryText);
                Set<String> seenIds = new LinkedHashSet<>();
                // 并行检索：多个变体同时查 PgVector
                List<CompletableFuture<List<Document>>> futures = expandedQueries.stream()
                        .map(q -> CompletableFuture.supplyAsync(() ->
                                vectorStore.similaritySearch(SearchRequest.builder()
                                        .query(q.text()).topK(3).similarityThreshold(0.5)
                                        .filterExpression(userFilter).build())))
                        .toList();
                for (CompletableFuture<List<Document>> future : futures) {
                    try {
                        List<Document> docs = future.get(5, TimeUnit.SECONDS);
                        for (Document doc : docs) {
                            String id = doc.getId();
                            if (id != null && seenIds.add(id)) { allDocs.add(doc); }
                        }
                    } catch (Exception e) {
                        log.warn("【{}】并行检索某个变体超时/失败，跳过：{}", name, e.getMessage());
                    }
                }
                log.info("【{}】Multi-Query RAG：{} 个变体 → 去重后 {} 条文档", name, expandedQueries.size(), allDocs.size());
            } else {
                allDocs = vectorStore.similaritySearch(SearchRequest.builder().query(userPrompt).topK(3).similarityThreshold(0.7).filterExpression(userFilter).build());
                log.info("【{}】单Query RAG：检索到 {} 条文档", name, allDocs.size());
            }
            if (CollUtil.isNotEmpty(allDocs)) {
                String docsContent = allDocs.stream().map(doc -> "参考文档：\n" + doc.getText()).collect(Collectors.joining("\n\n"));
                messageList.add(new SystemMessage("以下是与用户问题相关的参考资料，请优先基于这些资料回答；若资料无相关信息，再调用工具：\n" + docsContent));
            }
        } catch (Exception e) { log.error("【{}】RAG检索异常", name, e); }
    }

    public abstract String step();
    protected void cleanup() { }

    public void setQueryRewriter(QueryRewriter queryRewriter) { this.queryRewriter = queryRewriter; }

    public enum AgentState { IDLE, RUNNING, FINISHED, ERROR }
}
