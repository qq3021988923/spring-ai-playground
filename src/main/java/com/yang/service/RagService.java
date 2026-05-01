package com.yang.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 服务类（简化版，内存存储）
 * 学习第4步：理解 RAG 的基本原理
 */
@Slf4j
@Service
public class RagService {

    @Resource(name = "dashscopeChatModel")
    private ChatModel chatModel;

    // 内存存储：存储切分后的文档片段
    private final List<Document> documentChunks = new ArrayList<>();

    /**
     * 项目启动时，加载一些模拟的知识库
     */
    @PostConstruct
    public void initKnowledgeBase() {
        log.info("正在初始化知识库...");

        // 模拟知识库内容
        String knowledge = """
            # 公司产品介绍
            
            ## 产品A：智能聊天机器人
            - 价格：99元/月
            - 特点：支持多轮对话，语义理解准确
            - 适用场景：客服、个人助手
            
            ## 产品B：AI 文档分析
            - 价格：199元/月
            - 特点：自动摘要、关键词提取、智能问答
            - 适用场景：企业文档管理、知识库问答
            
            ## 产品C：AI 图像生成
            - 价格：299元/月
            - 特点：根据文字描述生成图片，支持多种风格
            - 适用场景：设计、营销素材制作
            
            # 常见问题
            
            Q: 可以免费试用吗？
            A: 可以，所有产品都支持7天免费试用。
            
            Q: 如何退款？
            A: 购买后30天内未使用超过10次，可以申请全额退款。
            
            Q: 支持团队协作吗？
            A: 支持，可以创建团队，共享资源。
            """;

        // 将文本转换为 Document 对象 ，将以上数据格式不变的存储进去
        Document document = new Document(knowledge);
        log.info("RAG文档对象 {}", document);
        // 使用 TokenTextSplitter 切分文档
        // 默认配置：每段 800 token，重叠 100 token
        // 就是给这个数据库起个目录 快速查询对应的数据
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(List.of(document));
        log.info("切分文档片段 {}", chunks);
        // 保存到内存
        documentChunks.addAll(chunks);
        log.info("知识库初始化完成，共 {} 个文档片段", chunks.size());
    }

    /**
     * 简单的相似度检索（这里用关键词匹配模拟）
     * 实际项目中会用向量相似度
     */
    private List<Document> retrieve(String query) {
        List<Document> result = new ArrayList<>();

        // 简单的关键词匹配
        for (Document chunk : documentChunks) {
            if (chunk.getText().toLowerCase().contains(query.toLowerCase())) {
                result.add(chunk);
            }
        }

        // 如果没找到匹配的，返回前2个片段
        if (result.isEmpty()) {
            result = documentChunks.size() > 2 ? documentChunks.subList(0, 2) : documentChunks;
        }

        log.info("检索到 {} 个相关文档片段", result.size());
        return result;
    }

    /**
     * RAG 问答：先检索知识库，再生成回答
     */
    public String ragChat(String userQuery) {
        // 第一步：检索相关文档
        List<Document> relevantDocs = retrieve(userQuery);

        // 第二步：将检索到的文档拼接成上下文
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < relevantDocs.size(); i++) {
            context.append("【资料 ").append(i + 1).append("】\n");
            context.append(relevantDocs.get(i).getText());
            context.append("\n\n");
        }

        // 第三步：构建 Prompt，让 AI 根据上下文回答
        String systemPrompt = """
            你是一个专业的客服助手。
            请根据以下【资料】回答用户的问题。
            如果资料中没有答案，请诚实告诉用户"抱歉，我在知识库中没有找到相关信息"。
            不要编造资料中没有的内容。
            
            【资料】
            %s
            """.formatted(context.toString());

        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userQuery)
        );

        ChatResponse response = chatModel.call(new Prompt(messages));
        String answer = response.getResult().getOutput().getText();

        log.info("用户问题：{}", userQuery);
        log.info("AI 回答：{}", answer);
        return answer;
    }

    /**
     * 获取当前知识库内容（调试用）
     */
    public List<String> getKnowledgeBase() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < documentChunks.size(); i++) {
            result.add("片段 " + (i + 1) + "\n" + documentChunks.get(i).getText());
        }
        return result;
    }

}