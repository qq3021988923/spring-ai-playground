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

    /**
     * 初始化恋爱知识库
     */
    public void initKnowledgeBase() {
        log.info("正在加载恋爱知识库...");

        try {
            // 1. 准备知识库内容（模拟恋爱常见问题）
            String knowledgeContent = """
                # 恋爱常见问题知识库
                
                ## 如何表白
                Q: 喜欢一个人不敢表白怎么办？
                A: 先建立自信，从小事开始关心对方，找合适时机真诚表达。
                
                Q: 表白被拒绝了怎么办？
                A: 保持尊严，感谢对方，给彼此空间，继续做自己。
                
                Q: 怎么判断对方喜不喜欢我？
                A: 看对方是否愿意花时间陪你，是否关心你的感受，是否主动联系你。
                
                ## 相处
                Q: 恋爱中如何保持新鲜感？
                A: 一起尝试新事物，给彼此空间，保持沟通，制造小惊喜。
                
                Q: 吵架了怎么和好？
                A: 冷静后主动道歉，换位思考，给对方台阶，有效沟通。
                
                Q: 异地恋怎么维持？
                A: 定期见面，保持每日联系，建立共同目标，信任彼此。
                
                ## 单身
                Q: 单身久了不想谈恋爱怎么办？
                A: 享受单身生活，提升自己，缘分到了自然来。
                
                Q: 如何扩大社交圈？
                A: 参加活动，培养爱好，保持真诚。
                """;

            // 2. 创建 Document
            List<Document> documents = new ArrayList<>();
            documents.add(new Document(knowledgeContent));

            // 3. 切分文档
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(documents);

            // 4. 添加到向量存储(数据库)
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
        // 把找到的最相关的 3条数据返回
        SearchRequest request = SearchRequest.builder().query(query).topK(3).build();

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

}