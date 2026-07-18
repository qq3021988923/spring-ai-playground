package com.yang.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 关键词自动丰富器
 *
 * <p><b>干什么的：</b></p>
 * 文档入库前，调大模型用中文给每篇内容提炼 3-5 个关键词，存到 metadata 里。
 *
 * <p><b>为什么不用 Spring AI 内置的 KeywordMetadataEnricher：</b></p>
 * 内置的那个默认用英文 prompt，输出的关键词是英文。比如中文文档"失恋恢复技巧"
 * 会被生成 "breakup recovery"，在中文检索场景下不如 "失恋恢复" 直接。
 * 自己写 ChatClient 发中文 prompt，输出纯中文关键词。
 *
 * <p><b>成本：</b></p>
 * 每篇文档多一次大模型调用（约 50-150 Token），只在入库时执行，查询时零开销。
 */
@Slf4j
@Component
public class KeywordEnricher {

    /**
     * 中文关键词生成 prompt
     * <p>
     * 告诉大模型：角色 → 要求 → 格式约束 → 示例 → 严格输出格式
     * 最后那行"只输出关键词，不要任何解释"是关键，防止大模型废话连篇
     */
    private static final String KEYWORD_PROMPT = """
            你是一个中文关键词提取专家。
            请阅读以下内容，提取3到5个最能概括核心主题的中文关键词。
            要求：
            1. 关键词必须是中文，2到8个字
            2. 关键词之间用中文逗号（，）分隔
            3. 只输出关键词，不要任何解释、编号或额外文字

            示例 — 内容：追求心仪对象时，首先要建立自信，保持真诚，多了解对方的兴趣爱好
            输出：追求技巧，自信提升，真诚沟通，了解对方

            现在请处理以下内容：
            """;

    private final ChatClient chatClient;

    public KeywordEnricher(ChatModel dashscopeChatModel) {
        // 建一个专门用于生成关键词的 ChatClient，不走 SYSTEM_PROMPT 的恋爱人设
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * 给每篇文档自动生成中文关键词
     *
     * @param documents 文档列表，每篇会被原地修改（metadata 里追加 excerpt_keywords 字段）
     * @return 同样的文档列表（原地修改后返回）
     */
    public List<Document> enrich(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        log.info("开始为 {} 篇文档生成中文关键词...", documents.size());
        int successCount = 0;

        for (Document doc : documents) {
            try {
                // 只取文档前 500 字发给大模型，省 Token。500 字足够提炼关键词
                String snippet = doc.getText();
                if (snippet.length() > 500) {
                    snippet = snippet.substring(0, 500);
                }

                String keywords = chatClient
                        .prompt()
                        .user(KEYWORD_PROMPT + snippet)
                        .call()
                        .content();

                // 清理：去掉可能的引号、换行、前后空格
                keywords = keywords.replaceAll("[\"'\\n\\r]", "").trim();

                // 如果大模型返回空了，跳过这篇
                if (keywords.isEmpty() || keywords.length() > 50) {
                    log.debug("跳过无效关键词：{}", keywords);
                    continue;
                }

                // 写入 metadata
                doc.getMetadata().put("excerpt_keywords", keywords);
                successCount++;
                log.debug("  关键词：{} ← {}", keywords,
                        snippet.substring(0, Math.min(30, snippet.length())));
            } catch (Exception e) {
                log.warn("单篇文档关键词生成失败：{}", e.getMessage());
                // 单篇失败不阻塞其他文档
            }
        }

        log.info("中文关键词生成完成：{} / {} 篇成功", successCount, documents.size());
        return documents;
    }
}
