package com.yang.agent;

import cn.hutool.core.util.StrUtil;
import com.yang.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 智能体抽象基类
 * 核心能力：
 * 1. 状态机管理（IDLE/RUNNING/FINISHED/ERROR）
 * 2. 多步任务循环执行（限制最大步骤）
 * 3. 同步调用 + SSE流式调用
 * 4. 对话上下文管理
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

    // ==================== 【核心修复】重置方法：每次调用都清空状态 ====================
    private void reset() {
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        this.messageList.clear();
        log.info("【{}】状态已重置：IDLE", name);
    }

    // ==================== 同步执行（非流式） ====================
    /**
     * 同步运行智能体
     * @param userPrompt 用户输入的问题
     * @return 最终执行结果
     */
    public String run(String userPrompt) {
        // 【修复】每次执行前强制重置状态，解决单例状态异常问题
        reset();

        // 状态校验：只有空闲状态才能执行
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("智能体状态异常，无法执行：" + this.state);
        }
        // 参数校验：用户输入不能为空
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("用户输入不能为空");
        }

        // 切换状态为运行中
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();

        try {
            // 多步循环：直到完成任务 or 达到最大步骤
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("执行步骤：{}/{}", currentStep, maxSteps);
                // 执行子类实现的具体步骤
                String stepResult = step();
                results.add("步骤 " + currentStep + "：" + stepResult);
            }

            // 超过最大步骤，强制结束
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("任务终止：已达到最大步骤数(" + maxSteps + ")");
            }

            // 【同步接口】最后追加AI正式回答
            String finalAnswer = getFinalAnswer();
            results.add("\n--- AI 正式回答 ---\n" + finalAnswer);
            return String.join("\n", results);
        } catch (Exception e) {
            // 异常处理
            state = AgentState.ERROR;
            log.error("智能体执行异常", e);
            return "执行错误：" + e.getMessage();
        } finally {
            // 执行完成/异常，清理资源
            this.cleanup();
        }
    }

    // ==================== 流式执行（SSE前端实时输出） ====================
    /**
     * SSE流式运行智能体
     * 作用：前端实时接收每一步的执行结果（类似ChatGPT流式打字）
     * @param userPrompt 用户问题
     * @return SseEmitter 流式响应对象
     */
    public SseEmitter runStream(String userPrompt) {
        // 【修复】每次执行前强制重置状态，解决单例状态异常问题
        reset();

        // 创建SSE对象，超时时间5分钟
        SseEmitter sseEmitter = new SseEmitter(300000L);

        // 异步执行（防止阻塞主线程）
        CompletableFuture.runAsync(() -> {
            try {
                // 基础校验
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：智能体状态异常：" + this.state, MediaType.TEXT_EVENT_STREAM);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：用户输入不能为空", MediaType.TEXT_EVENT_STREAM);
                    sseEmitter.complete();
                    return;
                }

                // 开始执行
                this.state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));

                // 多步循环执行
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    currentStep = i + 1;
                    log.info("执行步骤：{}/{}", currentStep, maxSteps);
                    String stepResult = step();
                    // 实时发送步骤结果到前端，指定MediaType解决乱码
                    sseEmitter.send("Step " + currentStep + "：" + stepResult, MediaType.TEXT_EVENT_STREAM);
                }

                // 结束提示
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    sseEmitter.send("执行结束：达到最大步骤数", MediaType.TEXT_EVENT_STREAM);
                }

                // ====================== 核心新增：步骤执行完，发送AI正式回答 ======================
                sseEmitter.send("\n========================================", MediaType.TEXT_EVENT_STREAM);
                sseEmitter.send("AI 正式回答：", MediaType.TEXT_EVENT_STREAM);
                String finalAnswer = getFinalAnswer();
                sseEmitter.send(finalAnswer, MediaType.TEXT_EVENT_STREAM);
                sseEmitter.send("========================================\n", MediaType.TEXT_EVENT_STREAM);

                sseEmitter.complete();

            } catch (Exception e) {
                // 异常处理
                state = AgentState.ERROR;
                log.error("流式执行异常", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage(), MediaType.TEXT_EVENT_STREAM);
                    sseEmitter.complete();
                } catch (Exception ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                this.cleanup();
            }
        });

        // SSE连接超时/完成的回调
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
            // 核心：直接取对话历史里的干净回答，不让AI重新编答案
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

    // ==================== 抽象方法 ====================
    /**
     * 抽象步骤方法
     * 子类必须实现：每一步具体要做什么
     */
    public abstract String step();

    /**
     * 清理方法
     * 子类可重写：执行完成后清理资源
     */
    protected void cleanup() {
        // 默认空实现
    }
}