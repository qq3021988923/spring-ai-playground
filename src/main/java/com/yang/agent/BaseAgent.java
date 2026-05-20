package com.yang.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private int maxSteps = 10;          // 最大执行步骤（防止死循环）
    // ==================== 核心依赖 ====================
    protected ChatClient chatClient;    // Spring AI 聊天客户端
    protected List<Message> messageList = new ArrayList<>(); // 对话上下文
    // ==================== 企业级记忆核心 ====================
    protected ChatMemory chatMemory;   // 短期上下文记忆（多轮对话）
    protected VectorStore vectorStore; // 长期向量库记忆（持久化存储）

    // ==================== 【修复】重置方法：接收外部会话ID，不再修改全局系统提示词 ====================
    private void reset(String conversationId) {
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        this.messageList.clear();

        // ✅ 修复1：从ChatMemory加载历史对话（上下文记忆生效）
        if (chatMemory != null) {
            List<Message> history = chatMemory.get(conversationId);
            if (!history.isEmpty()) {
                messageList.addAll(history);
                log.info("【{}】加载历史对话：{} 条，会话ID：{}", name, history.size(), conversationId);
            }
        }

        // ✅ 修复2：对话前自动检索向量库的相关历史（长期记忆生效）
        if (vectorStore != null && !messageList.isEmpty()) {
            // 用最后一条用户消息检索相关历史，更准确
            Message lastUserMsg = messageList.stream()
                    .filter(m -> m instanceof UserMessage)
                    .reduce((first, second) -> second)
                    .orElse(null);

            if (lastUserMsg != null) {
                List<Document> relatedDocs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(lastUserMsg.getText())
                                .topK(3)
                                .similarityThreshold(0.6)
                                .build()
                );

                if (!relatedDocs.isEmpty()) {
                    log.info("【{}】检索到相关历史对话：{} 条", name, relatedDocs.size());
                    // 把相关历史作为上下文加到消息列表开头，不修改全局系统提示词
                    StringBuilder historyContext = new StringBuilder("【相关历史对话参考】\n");
                    for (Document doc : relatedDocs) {
                        historyContext.append(doc.getText()).append("\n---\n");
                    }
                    messageList.add(0, new SystemMessage(historyContext.toString()));
                }
            }
        }

        log.info("【{}】状态已重置：IDLE", name);
    }

    // ==================== 同步执行（旧接口 /agent 调用） ====================
    public String run(String conversationId, String userPrompt) {
        reset(conversationId); // ✅ 接收外部传入的会话ID

        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("智能体状态异常，无法执行：" + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("用户输入不能为空");
        }

        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();

        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("执行步骤：{}/{}", currentStep, maxSteps);
                String stepResult = step();
                results.add("步骤 " + currentStep + "：" + stepResult);
            }

            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("任务终止：已达到最大步骤数(" + maxSteps + ")");
            }

            String finalAnswer = getFinalAnswer();
            results.add("\n--- AI 正式回答 ---\n" + finalAnswer);

            // ✅ 保存对话到向量库
            saveConversationToVectorStore(userPrompt, finalAnswer);
            // ✅ 保存对话到ChatMemory（上下文记忆生效）
            if (chatMemory != null) {
                chatMemory.add(conversationId, new UserMessage(userPrompt));
                chatMemory.add(conversationId, new org.springframework.ai.chat.messages.AssistantMessage(finalAnswer));
            }

            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("智能体执行异常", e);
            return "执行错误：" + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    // ==================== 流式执行（新接口 /manus/stream 调用） ====================
    public SseEmitter runStream(String conversationId, String userPrompt) {
        reset(conversationId); // ✅ 接收外部传入的会话ID

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
                    sseEmitter.send("Step " + currentStep + "：" + stepResult);
                    finalAnswer = stepResult;
                }

                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    sseEmitter.send("执行结束：达到最大步骤数");
                }

                sseEmitter.send("\n========================================");
                sseEmitter.send("AI 正式回答：");
                finalAnswer = getFinalAnswer();
                sseEmitter.send(finalAnswer);
                sseEmitter.send("========================================\n");

                // ✅ 保存对话到向量库
                saveConversationToVectorStore(userPrompt, finalAnswer);
                // ✅ 保存对话到ChatMemory（上下文记忆生效）
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
    // ==================== 通用方法：获取AI最终正式回答 ====================
    private String getFinalAnswer() {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .messages(messageList)
                    .call()
                    .content()
                    .replaceAll("\\{.*?\\}", "")
                    .replace("doTerminate","")
                    .trim();
        } catch (Exception e) {
            log.error("生成最终回答异常", e);
            return "⚠️ 无法联网，严格遵守规则不编造答案！";
        }
    }

    // ==================== 向量库存储（长期记忆 - 企业级核心） ====================
    protected void saveConversationToVectorStore(String userQuestion, String agentAnswer) {
        if (vectorStore == null) {
            log.debug("向量库未配置，跳过长期记忆存储");
            return;
        }

        try {
            String content = "用户问题：" + userQuestion + "\nAI回答：" + agentAnswer;
            Document document = new Document(content);
            vectorStore.add(List.of(document));
            log.info("✅ 【{}】对话已保存至向量库（长期记忆生效）", name);
        } catch (Exception e) {
            log.error("❌ 【{}】向量库存储失败", name, e);
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