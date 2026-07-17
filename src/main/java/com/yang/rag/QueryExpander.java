package com.yang.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多 Query 扩展器
 *
 * <p><b>干什么的：</b></p>
 * 用户口语化地问一个问题（比如"怎么追女生"），大模型把它扩展成 4 个不同角度的搜索关键词。
 * 每个关键词去向量库搜一次，捞回来的文档覆盖面比单关键词大得多。
 *
 * <p><b>数据流：</b></p>
 * <pre>
 * 输入："怎么追女生"
 *   ↓ 调一次大模型（额外花 token）
 * 输出 4 个变体：
 *   变体1（原始）："怎么追女生"
 *   变体2（扩展）："追求心仪女生的方法和技巧"
 *   变体3（扩展）："如何提升个人魅力吸引异性"
 *   变体4（扩展）："建立恋爱关系的心态和沟通方式"
 *   ↓
 * 4 个变体各自去 PgVector 搜 topK=3，最多捞 12 条
 *   ↓
 * 按文档 ID 去重，最终 N 条注入 LLM 上下文
 * </pre>
 *
 * <p><b>成本：</b></p>
 * 每次调用多一次大模型 API 请求（Query 扩展），约消耗 100-300 Token。
 * 对 PgVector 多 3 次 HNSW 索引查询（单次 < 10ms），总延迟增加约 30ms。
 *
 * <p><b>容错：</b></p>
 * 如果大模型调用失败（网络抖动 / API 限流），降级为只使用原始问题，保证功能不挂。
 *
 * @see MultiQueryExpander Spring AI 内置的多查询扩展器
 */
@Slf4j
@Component
public class QueryExpander {

    /**
     * Spring AI 提供的多查询扩展器
     *
     * numberOfQueries(3)：除了原始问题外，额外生成 3 个变体
     * includeOriginal(true)：结果列表里第 1 个是原始问题，后面 3 个是扩展的，共 4 个
     */
    private final MultiQueryExpander expander;

    public QueryExpander(ChatModel dashscopeChatModel) {
        // 用同一个大模型（qwen-plus）来做 Query 扩展
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        this.expander = MultiQueryExpander.builder()
                .chatClientBuilder(builder)    // 指定用哪个大模型来扩展
                .numberOfQueries(3)            // 生成 3 个扩展变体
                .includeOriginal(true)         // 保留原始问题，总共返回 1 + 3 = 4 个
                .build();
    }

    /**
     * 把用户的原始问题扩展成多个变体
     *
     * @param originalQuery 用户输入的原始问题，比如 "怎么和女朋友和好"
     * @return 扩展后的查询列表（第 1 个是原始问题，后面是扩展变体），至少包含 1 个原始问题
     */
    public List<Query> expand(String originalQuery) {
        try {
            // 调大模型：请帮我把这个问题从 3 个不同角度重写
            List<Query> queries = expander.expand(new Query(originalQuery));
            log.info("Query 扩展：{} → {} 个变体", originalQuery, queries.size());
            return queries;
        } catch (Exception e) {
            // 大模型调用失败时的兜底：降级为只搜原始问题
            // 虽然检索覆盖面变小了，但功能不会中断
            log.warn("Query 扩展失败，降级使用原始问题：{}", e.getMessage());
            return List.of(new Query(originalQuery));
        }
    }
}
