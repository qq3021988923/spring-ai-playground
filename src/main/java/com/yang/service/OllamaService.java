package com.yang.service;

import com.yang.rag.LoveDocumentLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OllamaService {

    @Resource(name = "ollamaChatModel")
    private ChatModel ollamaChatModel;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private LoveDocumentLoader loveDocumentLoader;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Resource
    private ChatMemory chatMemory;

    public String fullAgentChat(String userMessage) {
        ChatClient localClient = ChatClient.builder(ollamaChatModel).build();

        String systemPrompt = """
                你是智能助手小智，一个能在本地运行、拥有多种能力的 AI。
                你可以使用 MCP 提供的远程工具来帮助用户。
                工作步骤：
                1. 理解用户需求，判断是否需要工具
                2. 调用工具获取信息
                3. 整合信息给出最终答案
        """;

        String historyContext = loveDocumentLoader.search(userMessage).stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String answer = localClient.prompt()
                .system(systemPrompt)
                .user("知识库相关内容：\n" + historyContext + "\n\n用户问题：" + userMessage)
                .toolCallbacks(toolCallbackProvider)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .call()
                .content();

        String newKnowledge = """
            用户问题：%s
            智能助手回答：%s
            """.formatted(userMessage, answer);
        Document newDoc = new Document(newKnowledge);
        vectorStore.add(List.of(newDoc));
        log.info("本地 Agent 对话已存入知识库");

        return answer;
    }
}
