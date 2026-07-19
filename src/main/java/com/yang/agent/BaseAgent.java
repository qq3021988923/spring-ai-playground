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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 企业级智能体抽象基类（已修复所有记忆问题）
 * 功能：状态机管理 + 同步执行 + SSE流式执行 + 上下文临时记忆 + 向量库长期记忆
 */
@Data
@Slf4j
public abstract class BaseAgent {
    // ==================== 基础配置 ====================
    private String name;                // 智能体名称
    private String systemPrompt;        // 系统提示词（人设/规则）
    private String nextStepPrompt;      // 下一步执行提示词
    private AgentState state = AgentState.IDLE; // 当前状态
    private int currentStep = 0;        // 当前执行到第几步
    private int maxSteps = 5;          // 最大执行步骤（防止死循环）
    // ==================== 核心依赖 ====================
    protected ChatClient chatClient;    // Spring AI 聊天客户端
    protected List<Message> messageList = new ArrayList<>(); // 对话上下文
    // ==================== 企业级记忆核心 ====================
    protected ChatMemory chatMemory;   // 短期上下文记忆（多轮对话）
    protected VectorStore vectorStore; // 长期向量库记忆（持久化存储）
    protected QueryExpander queryExpander; // 多 Query 扩展检索
    protected QueryRewriter queryRewriter; // Query 智能改写
    protected Consumer<String> statusConsumer; // Flux 状态推送回调


    // ==================== 【修复】重置方法：接收外部会话ID，不再修改全局系统提示词 ====================
    private void reset(String conversationId) {
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        this.messageList.clear();


        if (chatMemory != null) {
            List<Message> history = chatMemory.get(conversationId); // ChatMemory加载历史对话（上下文记忆生效）
            if (!history.isEmpty()) {
                messageList.addAll(history);
                log.info("【{}】加载历史对话：{} 条，会话ID：{}", name, history.size(), conversationId);
            }
        }

        log.info("【{}】状态已重置：IDLE", name);
    }

    // ==================== 同步执行（旧接口 /agent 调用） ====================
    public String run(String conversationId, String userPrompt) {
        return executeCoreLogic(conversationId, userPrompt);
    }
    //  核心：流式执行入口
    public SseEmitter runStream(String conversationId, String userPrompt) {
        reset(conversationId); //   先加载历史对话

        SseEmitter sseEmitter = new SseEmitter(300000L); // 5分钟超时

        //  异步执行，不阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：智能体状态异常：" + this.state);
                    sseEmitter.complete(); // 作用：结束流式输出，接口调用完成
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：用户输入不能为空");
                    sseEmitter.complete();
                    return;
                }

                this.state = AgentState.RUNNING;

                // 把你发的消息，加入 AI 的对话上下文
                messageList.add(new UserMessage(userPrompt));
                String finalAnswer = "";

                //  控制 AI 最多思考 5 次，防止死循环
                //  多步循环：think() → act()
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    currentStep = i + 1;
                    log.info("执行步骤：{}/{}", currentStep, maxSteps);
                    String stepResult =step();
                    //   只有非空的最终回答才发给前端  并且状态必须是完整的状态 就是要思考5次
                    if (StrUtil.isNotBlank(stepResult) && state == AgentState.FINISHED) {
                        finalAnswer = stepResult;
                    }
                }

                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    finalAnswer = "任务终止：已达到最大步骤数(" + maxSteps + ")";
                }

                //   只给前端发最终的干净回答，没有任何技术细节
                sseEmitter.send(finalAnswer);

                // 保存对话到数据库
                saveConversationToVectorStore(userPrompt, finalAnswer, conversationId);
                if (chatMemory != null) {
                    chatMemory.add(conversationId, new UserMessage(userPrompt));
                    chatMemory.add(conversationId, new org.springframework.ai.chat.messages.AssistantMessage(finalAnswer));
                }

                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("流式执行异常", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (Exception ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                this.cleanup();
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE连接超时");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            cleanup();
            log.info("SSE连接正常关闭");
        });
        return sseEmitter;
    }

   /* // ==================== 流式执行（/manus/stream 调用） ====================
    public SseEmitter runStream(String conversationId, String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5分钟超时

        CompletableFuture.runAsync(() -> {
            try {
                // 调用完全一样的核心逻辑
                String finalAnswer = executeCoreLogic(conversationId, userPrompt);
                // 只做流式输出这一件事
                sseEmitter.send(finalAnswer);
                sseEmitter.complete();
            } catch (Exception e) {
                log.error("流式执行异常", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (Exception ex) {
                    sseEmitter.completeWithError(ex);
                }
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE连接超时");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            cleanup();
            log.info("SSE连接正常关闭");
        });

        return sseEmitter;
    }
*/
    // ==================== 核心执行逻辑（两个接口共用） ====================
    private String executeCoreLogic(String conversationId, String userPrompt) {
        reset(conversationId); // 当前对话的【上下文记忆】就彻底没了！

        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("智能体状态异常，无法执行：" + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("用户输入不能为空");
        }

        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt)); //用户的问题

        // ========== 新增：RAG核心 - 检索向量库相关文档 ==========
        // 有数据会放到 messageList 临时存储中
        addRagContextToMessageList(userPrompt);

        String finalAnswer = "";

        try {
            //  ReAct = Reasoning（推理） + Acting（执行 / 调用工具）
            // 思考 → 调用工具 → 观察结果 → 再思考 → 再调用工具 → 得出答案
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("执行步骤：{}/{}", currentStep, maxSteps);
                String stepResult = step();
                // 和流接口完全一样的结果过滤逻辑
                if (StrUtil.isNotBlank(stepResult) && state == AgentState.FINISHED) {
                    finalAnswer = stepResult;
                }
            }

            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                finalAnswer = "任务终止：已达到最大步骤数(" + maxSteps + ")";
            }

            // 将数据存储本地数据库 用户原始问题 最终回答响应
            saveConversationToVectorStore(userPrompt, finalAnswer, conversationId);
            if (chatMemory != null) { // 添加上下文记忆
                chatMemory.add(conversationId, new UserMessage(userPrompt));
                chatMemory.add(conversationId, new org.springframework.ai.chat.messages.AssistantMessage(finalAnswer));
            }

            return finalAnswer;
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("智能体执行异常", e);
            return "执行错误：" + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    // ==================== 向量库存储（与恋爱大师对齐：质量过滤 + 用户标签） ====================
    protected void saveConversationToVectorStore(String userQuestion, String agentAnswer, String conversationId) {
        if (vectorStore == null) {
            log.debug("向量库未配置，跳过长期记忆存储");
            return;
        }

        // ===== 质量过滤（与 LoveAdvisorService 对齐） =====
        if (agentAnswer == null || agentAnswer.length() < 10) {
            log.info("【{}】回答太短({}字)，跳过存入向量库", name, agentAnswer == null ? 0 : agentAnswer.length());
            return;
        }
        if (agentAnswer.contains("抱歉") || agentAnswer.contains("我只能回答")) {
            log.info("【{}】兜底回复，跳过存入向量库", name);
            return;
        }

        try {
            String content = "用户问题：" + userQuestion + "\nAI回答：" + agentAnswer;
            Document document = new Document(content);
            document.getMetadata().put("userId", conversationId);
            document.getMetadata().put("type", "conversation");
            document.getMetadata().put("timestamp", System.currentTimeMillis());
            vectorStore.add(List.of(document));
            log.info("✅ 【{}】高质量问答已存入向量库", name);
        } catch (Exception e) {
            log.error("❌ 【{}】向量库存储失败", name, e);
        }
    }
    /**
     * 封装RAG检索逻辑：查询向量库并将结果加入AI上下文
     * @param userPrompt 用户的问题
     */
    /**
     * RAG检索：Multi-Query 扩展 → 多角度检索 → 去重合并
     * <p>
     * 如果有 QueryExpander，先扩展为多个变体再从不同角度检索；
     * 没有 QueryExpander 则降级为单 Query 检索。
     */
    private void addRagContextToMessageList(String userPrompt) {
        if (vectorStore == null) {
            return;
        }

        try {
            // 智能改写：短问题调用 QueryRewriter，长问题直接用
            String queryText = userPrompt;
            if (queryRewriter != null && userPrompt.length() < 3) {
                queryText = queryRewriter.doQueryRewrite(userPrompt);
            }

            List<Document> allDocs = new ArrayList<>();

            if (queryExpander != null) {
                // ===== Multi-Query 扩展检索 =====
                List<Query> expandedQueries = queryExpander.expand(queryText);
                Set<String> seenIds = new LinkedHashSet<>();

                for (Query q : expandedQueries) {
                    List<Document> docs = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(q.text())
                                    .topK(3)
                                    .similarityThreshold(0.5)
                                    .build()
                    );
                    for (Document doc : docs) {
                        String id = doc.getId();
                        if (id != null && seenIds.add(id)) {
                            allDocs.add(doc);
                        }
                    }
                }
                log.info("【{}】Multi-Query RAG：{} 个变体 → 去重后 {} 条文档", name, expandedQueries.size(), allDocs.size());
            } else {
                // ===== 单 Query 检索（降级） =====
                allDocs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(userPrompt)
                                .topK(3)
                                .similarityThreshold(0.7)
                                .build()
                );
                log.info("【{}】单Query RAG：检索到 {} 条文档", name, allDocs.size());
            }

            if (CollUtil.isNotEmpty(allDocs)) {
                String retrievedDocsContent = allDocs.stream()
                        .map(doc -> "参考文档：\n" + doc.getText())
                        .collect(Collectors.joining("\n\n"));
                messageList.add(new SystemMessage(
                        "以下是与用户问题相关的参考资料，请优先基于这些资料回答；若资料无相关信息，再调用工具：\n"
                                + retrievedDocsContent
                ));
            } else {
                log.info("【{}】RAG未检索到相关文档，将基于工具调用/通用知识回答", name);
            }
        } catch (Exception e) {
            log.error("【{}】RAG检索异常", name, e);
        }
    }
    // ==================== Flux 流式执行（逐字推送 + 步骤可见） ====================
    public Flux<String> runStreamFlux(String conversationId, String userPrompt) {
        return Flux.create(sink -> {
            this.statusConsumer = msg -> sink.next(msg);
            reset(conversationId);

            if (this.state != AgentState.IDLE) {
                sink.next("[ERROR] 智能体状态异常：" + this.state);
                sink.complete();
                return;
            }
            if (StrUtil.isBlank(userPrompt)) {
                sink.next("[ERROR] 用户输入不能为空");
                sink.complete();
                return;
            }

            this.state = AgentState.RUNNING;
            messageList.add(new UserMessage(userPrompt));

            // 动态步数：短问题 3 步，复杂问题 10 步
            int dynamicMaxSteps = userPrompt.length() < 10 ? 3 : getMaxSteps();
            sink.next("[STATUS] 正在分析问题...（最多思考 " + dynamicMaxSteps + " 步）");

            String finalAnswer = "";

            for (int i = 0; i < dynamicMaxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("执行步骤：{}/{}", currentStep, dynamicMaxSteps);
                String stepResult = step();

                // 步骤结果不为空且未完成 → 中间状态
                if (StrUtil.isBlank(stepResult) && state != AgentState.FINISHED) {
                    sink.next("[STATUS] 正在执行第 " + currentStep + " 步...");
                }

                // 最终回答 → 推送内容
                if (StrUtil.isNotBlank(stepResult) && state == AgentState.FINISHED) {
                    finalAnswer = stepResult;
                }
            }

            if (currentStep >= dynamicMaxSteps && state != AgentState.FINISHED) {
                state = AgentState.FINISHED;
                finalAnswer = "任务终止：已达到最大步骤数(" + dynamicMaxSteps + ")";
            }

            // 推送最终回答
            if (StrUtil.isNotBlank(finalAnswer)) {
                sink.next(finalAnswer);
            } else {
                sink.next("思考完成，当前对话无需调用工具");
            }

            // 保存记忆
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

    public void setQueryRewriter(QueryRewriter queryRewriter) {
        this.queryRewriter = queryRewriter;
    }

    /** 向 Flux 推送状态消息（子类 ToolCallAgent 调用） */
    public void pushStatus(String msg) {
        if (statusConsumer != null) {
            statusConsumer.accept(msg);
        }
    }

    // ==================== 抽象方法 ====================
    public abstract String step();

    protected void cleanup() {
        // 默认空实现
    }

    // ==================== 状态枚举 ====================
    public enum AgentState {
        IDLE,     // 空闲状态（初始状态，可接收新任务）
        RUNNING,  // 运行中（正在执行多步任务）
        FINISHED, // 执行完成（任务正常结束）
        ERROR     // 执行异常（任务出错）
    }


}