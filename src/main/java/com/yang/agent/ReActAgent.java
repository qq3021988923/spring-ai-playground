package com.yang.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;

@Slf4j
public class ReActAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ToolCallbackProvider mcpToolProvider;

    public ReActAgent(ChatClient chatClient,
                      ChatMemory chatMemory,
                      ToolCallbackProvider mcpToolProvider) {
        super("智能助手小智", "一个会思考、会用工具的 AI 助手");
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.mcpToolProvider = mcpToolProvider;
    }

    @Override
    public String execute(String userInput) {
        log("收到任务：" + userInput);
        log("========== Agent 开始工作 ==========");

        int mcpCount = 0;
        try {
            var mcpTools = mcpToolProvider.getToolCallbacks();
            if (mcpTools != null) {
                mcpCount = mcpTools.length;
            }
        } catch (Exception e) {
            log.warn("MCP 工具获取失败: {}", e.getMessage());
        }
        log.info("MCP工具数量: {}", mcpCount);

        String systemPrompt = """
        你是 %s，%s。
        你可以使用 MCP 提供的远程工具来帮助用户。
        请按步骤工作：先理解需求→决定是否用工具→调用工具获取结果→整合信息给出最终答案。
        语言：中文，简洁专业。禁止重复内容、空洞套话。输出 Markdown 格式。
        """.formatted(agentName, agentDescription);

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(userInput)
                .toolCallbacks(mcpToolProvider)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .call()
                .content();

        log("========== Agent 结束工作 ==========");
        return answer;
    }
}
