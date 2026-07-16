package com.yang.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 自定义 Re2 Advisor
 * 可提高大型语言模型的推理能力 改写提示词增强推理
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * 执行请求前，改写 Prompt
     *
     * @param request
     * @return
     */
    private ChatClientRequest before(ChatClientRequest request) {
        //取出你输入的原话，比如 "女朋友生气了怎么办"
        String userText = request.prompt().getUserMessage().getText();

        // 原话存进上下文中，方便后续代码通过 key "re2_input_query" 取出来用
        request.context().put("re2_input_query", userText);
        // 把"你的原话"塞进一个模版里，原话说一遍、末尾重复一遍。
        String newUserText = """
                %s
                Read the question again: %s
                """.formatted(userText, userText);
        // 重新封装提示词
        Prompt newPrompt = request.prompt().augmentUserMessage(newUserText);
        return new ChatClientRequest(newPrompt, request.context());
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        return chain.nextCall(this.before(chatClientRequest));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        return chain.nextStream(this.before(chatClientRequest));
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
