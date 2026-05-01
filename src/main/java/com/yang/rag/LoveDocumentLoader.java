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
 * 模仿鱼皮项目的 LoveApp
 */
@Slf4j
@Component
public class LoveDocumentLoader {

    private final VectorStore vectorStore;

    public LoveDocumentLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void initKnowledgeBase() {
        log.info("正在加载恋爱知识库...");
        try { // 读取本地初始化数据
            TextReader reader = new TextReader(new ClassPathResource("document/love-knowledge.md"));
            List<Document> documents = reader.get();  // 读取文件
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(documents);
            vectorStore.add(chunks);
            log.info("知识库加载完成！共 {} 个文档片段", chunks.size());
        } catch (Exception e) {
            log.error("加载知识库失败", e);
        }
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
        这是 PostgreSQL 专用的、带向量计算的 SQL，
      普通的 MySQL 没有这种功能。
        SELECT content, metadata,
        1 - (embedding <=> ai_embedding('如何表白')) AS similarity -- 计算余弦相似度
        FROM vector_store
        ORDER BY embedding <=> ai_embedding('如何表白') -- 按与问题向量的距离排序
        LIMIT 3;这就是你设置的 topK(3)
        */
        return vectorStore.similaritySearch(request);
    }

    //  vector_store 表里所有知识片段一次性删除 目前
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

    public void addKnowledge(String content) {
        Document doc = new Document(content);
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(List.of(doc));
        vectorStore.add(chunks);
        log.info("新增知识片段：{} 条", chunks.size());
    }

}