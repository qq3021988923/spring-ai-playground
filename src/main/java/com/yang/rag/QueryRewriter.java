package com.yang.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * Query 改写器：用 AI 把用户口语化的问题改成更适合向量检索的关键词
 * <p>
 * 例如："怎么追喜欢的女生" → "追求心仪对象的方法和技巧"
 */
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    /** 优化 RAG 检索命中率
     * 你输入："我喜欢的女生不理我了怎么办"
     *     ↓ AI 改写
     * 改写后："追求对象不理睬的处理方法和沟通技巧"
     *     ↓ 拿这个去 PgVector 搜
     * 命中率更高
     */
    public String doQueryRewrite(String prompt) {
        // 把用户输入的字符串包装成 Spring AI 的 Query 对象
        Query query = new Query(prompt);
        // 调用 AI 改写（内部调了一次 LLM，额外花一次 API 费用）
        Query transformedQuery = queryTransformer.transform(query);
        return transformedQuery.text(); // 取出改写后的纯文本返回
    }
}