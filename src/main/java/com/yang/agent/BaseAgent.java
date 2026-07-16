package com.yang.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import cn.hutool.core.collection.CollUtil;
import java.util.stream.Collectors;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
                saveConversationToVectorStore(userPrompt, finalAnswer);
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
            saveConversationToVectorStore(userPrompt, finalAnswer);
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
    /**
     * 封装RAG检索逻辑：查询向量库并将结果加入AI上下文
     * @param userPrompt 用户的问题
     */
    private void addRagContextToMessageList(String userPrompt) {
        // 向量库未配置则直接返回
        if (vectorStore == null) {
            return;
        }

        try {
            // 1. 构建向量库检索请求（RAG核心：根据用户问题查相似数据）
            SearchRequest searchRequest = SearchRequest.builder()
                    // 2. 设置检索关键词：用户当前输入的问题（用来做语义匹配）
                    .query(userPrompt)
                    // 3. 设置返回条数：最多返回3条最相似的文档（可改3/5/8）
                    .topK(3)
                    // 4. 设置相似度阈值：只保留相似度≥0.7的结果（过滤不相关内容）
                    .similarityThreshold(0.7)
                    // 5. 构建最终的检索请求对象
                    .build();

            // 2. 执行检索
            List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

            if (CollUtil.isNotEmpty(relevantDocs)) {
                log.info("【{}】RAG检索到相关文档 {} 条", name, relevantDocs.size());
                // 3. 拼接检索结果，以SystemMessage形式加入上下文（避免混淆用户/助手消息）
                String retrievedDocsContent = relevantDocs.stream()
                        // 给每一条检索结果加标题，格式化文本
                        .map(doc -> "参考文档：\n" + doc.getText())
                        // 把所有结果拼接成一整段字符串
                        .collect(Collectors.joining("\n\n"));
                System.out.println("我是向量数据库检索的数据\n" + retrievedDocsContent);
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