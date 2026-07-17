package com.yang.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 恋爱知识库加载器
 */
@Slf4j
@Component
public class LoveDocumentLoader {

    private final VectorStore vectorStore;

    public LoveDocumentLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 三篇知识库文件与其对应状态标签 */
    private static final String[][] KNOWLEDGE_FILES = {
            {"document/恋爱常见问题和回答-单身篇.md", "单身"},
            {"document/恋爱常见问题和回答-恋爱篇.md", "恋爱"},
            {"document/恋爱常见问题和回答-已婚篇.md", "已婚"}
    };

    public void initKnowledgeBase() {
        log.info("正在加载恋爱知识库（按状态分类）...");
        TokenTextSplitter splitter = new TokenTextSplitter();
        int totalChunks = 0;
        for (String[] file : KNOWLEDGE_FILES) {
            try {
                TextReader reader = new TextReader(new ClassPathResource(file[0]));
                List<Document> documents = reader.get();
                List<Document> chunks = splitter.apply(documents);
                // 给每个文档片段打上状态标签
                chunks.forEach(doc -> doc.getMetadata().put("status", file[1]));
                vectorStore.add(chunks);
                log.info("  {} → {} 个片段，状态标签：{}", file[0], chunks.size(), file[1]);
                totalChunks += chunks.size();
            } catch (Exception e) {
                log.error("加载 {} 失败", file[0], e);
            }
        }
        log.info("知识库加载完成！共 {} 个文档片段（单身/恋爱/已婚）", totalChunks);
    }

    /**
     * 检索相关文档
     */
    public List<Document> search(String query) {
        log.info("正在检索：{}", query);
        // SearchRequest 搜索请求
        // 把找到的最相关的 5条数据返回
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(5)   //　支持 TopK 检索 返回前5个，覆盖面更广
                .similarityThreshold(0.7)  // 相似度阈值过滤 　只返回真正相关的
                .build();

 /*
          去数据库里执行搜索
         vectorStore 连 PgVector、怎么执行 SQL、怎么计算向量距离。
         similaritySearch 实际的搜索动作
        vectorStore.similaritySearch(request) 等价于
        LIMIT 3;这就是你设置的 topK(3)
        */
        return vectorStore.similaritySearch(request);
    }

    //  清空 PgVector 里所有知识文档，从零开始重建
    public void clearKnowledgeBase() {
        log.warn("正在清空知识库...");
        // 简单方案：调用 vectorStore 的删除方法，传入空搜索获取所有ID
        List<Document> all = vectorStore.similaritySearch(
                SearchRequest.builder().query("").topK(1000).build()
        );
        List<String> ids = all.stream()
                .map(Document::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
        }
        log.info("知识库已清空");
    }

    // 动往 PgVector 加一条知识
    public void addKnowledge(String content) {
        Document doc = new Document(content);
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(List.of(doc));
        vectorStore.add(chunks);
        log.info("新增知识片段：{} 条", chunks.size());
    }

}