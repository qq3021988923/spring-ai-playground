package com.yang.tools;

import com.yang.rag.LoveDocumentLoader;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 恋爱知识库工具（AI 可调用的注解版工具）
 */
@Component
public class KnowledgeTools {

    private final LoveDocumentLoader loveDocumentLoader;

    // 注入你现有的知识库加载器
    public KnowledgeTools(LoveDocumentLoader loveDocumentLoader) {
        this.loveDocumentLoader = loveDocumentLoader;
    }

    /**
     * AI 工具：搜索恋爱知识库
     */
    @Tool(description = "在恋爱知识库中搜索相关建议和心得，输入问题关键词，返回相关知识片段")
    public String searchLoveKnowledge(
            @ToolParam(description = "要搜索的恋爱问题关键词") String query) {

        List<Document> docs = loveDocumentLoader.search(query);

        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }
}