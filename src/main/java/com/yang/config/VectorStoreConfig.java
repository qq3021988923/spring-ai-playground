package com.yang.config;

import com.yang.rag.LoveDocumentLoader;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
//public class VectorStoreConfig {
//
//    @Bean
//    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
//        // 变成一个有脑子的记忆仓库
//        // 精准命中，能找到意思最接近的
//        return SimpleVectorStore.builder(embeddingModel).build();
//    }
//
//}