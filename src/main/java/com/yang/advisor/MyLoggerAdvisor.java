package com.yang.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 日志拦截器 —— 在调用 LLM 前后打印请求和响应内容，方便调试。
 *
 * <h3>大白话理解</h3>
 * 你可以把它当成 Spring MVC 的 Interceptor 或者 AOP 的 @Around 切面：
 * <pre>
 *   around(请求):
 *       before()   → 打印"发给 AI 的 prompt 长什么样"
 *       proceed()  → 真正调用 LLM
 *       after()    → 打印"AI 回复了什么"
 * </pre>
 *
 * <h3>为什么同时实现两个接口</h3>
 * <ul>
 *   <li><b>CallAdvisor</b>   —— 拦截普通（同步）调用。LLM 一句话全部返回时走这里。</li>
 *   <li><b>StreamAdvisor</b> —— 拦截流式（SSE）调用。LLM 一个字一个字往外蹦时走这里。本项目用的就是流式。</li>
 * </ul>
 * 两个接口各写一套逻辑，是因为流式场景下响应是分段到达的，不能像同步那样直接拿完整结果。
 *
 * <h3>挂载方式</h3>
 * 使用时通过 ChatClient.builder() 注册：
 * <pre>
 *   ChatClient.builder(...)
 *       .defaultAdvisors(new MyLoggerAdvisor())
 *       .build();
 * </pre>
 * 注册后每次调用 LLM 都会自动触发，不需要在业务代码里手动调用。
 *
 * <h3>类比</h3>
 * 相当于给 HTTP 请求加了个 Filter，在请求到达"目标方法"前后各插了一脚。
 *
 * @see CallAdvisor     同步调用拦截
 * @see StreamAdvisor   流式调用拦截
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * 拦截器名称，Spring AI 内部用它来标识这个 Advisor。
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 执行优先级。数值越小越先执行。
     * 0 表示默认优先级，如果后续有多个 Advisor，可以调整这个值来控制执行顺序。
     */
    @Override
    public int getOrder() {
        return 0;
    }

    // ==================== 前置 & 后置 逻辑（两个接口复用） ====================

    /**
     * 前置拦截：在请求发给 LLM 之前执行。
     * 这里只是打印日志，不修改请求内容。
     * 如果你想改 prompt（比如追加一段话），也可以在这里改完再 return。
     */
    private ChatClientRequest before(ChatClientRequest request) {
        log.info("AI Request: {}", request.prompt());
        return request;
    }

    /**
     * 后置拦截：拿到 LLM 完整回复后执行。
     * 这里只是打印日志。
     */
    private void observeAfter(ChatClientResponse chatClientResponse) {
        log.info("AI Response: {}", chatClientResponse.chatResponse().getResult().getOutput().getText());
    }

    // ==================== 同步调用拦截 ====================
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        ChatClientResponse chatClientResponse = chain.nextCall(chatClientRequest);
        //— 看一眼完整的 AI 回复，控制台里打印一行 AI Response: xxx。纯偷看，不修改。
        observeAfter(chatClientResponse);
        return chatClientResponse; //把结果还给上一步
    }

    // ==================== 流式调用拦截 ====================
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        // 你发的消息到达，打印 "AI Request: 女朋友生气了怎么办"（控制台立刻看到）
        chatClientRequest = before(chatClientRequest);
        // 真正调用阿里云 API，返回一个 Flux 流此时还没数据，只是个"管道"）
        Flux<ChatClientResponse> chatClientResponseFlux = chain.nextStream(chatClientRequest);
//        收集完所有ai回复的内容 统一打印到控制台！ 默认是一个一个字的输出的
        return new ChatClientMessageAggregator()
                .aggregateChatClientResponse(chatClientResponseFlux, this::observeAfter);
    }
}