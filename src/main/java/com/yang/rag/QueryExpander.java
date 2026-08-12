package com.yang.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 多 Query 扩展器（三层防护：缓存 → 超时 → 重试 → 降级）
 *
 * <p><b>数据流：</b></p>
 * <pre>
 * 输入："怎么追女生"
 *   → ① 查缓存，命中直接返回
 *   → ② 缓存没中，调大模型扩展（最多等 3 秒）
 *   → ③ 超时或失败，重试 1 次（间隔 500ms）
 *   → ④ 全部失败，降级为只搜原始问题
 * 输出 2 个变体（1 原始 + 1 扩展）
 * </pre>
 *
 * @see MultiQueryExpander Spring AI 内置的多查询扩展器
 */
@Slf4j
@Component
public class QueryExpander {

    private final MultiQueryExpander expander;

    /** 缓存：同一个问题不重复调大模型扩展，最多存 100 条 */
    private final Map<String, List<Query>> cache =
            Collections.synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > 100;    // 超过 100 条自动踢掉最早的
                }
            });

    public QueryExpander(@Qualifier("deepseekFlashChatModel") ChatModel flashChatModel) {
        ChatClient.Builder builder = ChatClient.builder(flashChatModel);
        this.expander = MultiQueryExpander.builder()
                .chatClientBuilder(builder)    // 指定用哪个大模型来扩展
                .numberOfQueries(1)            // 生成 1 个扩展变体
                .includeOriginal(true)         // 保留原始问题，总共返回 1 + 1 = 2 个
                .build();
    }

    // ===== 公开入口：先查缓存 → 调大模型 → 兜底降级 =====
    /**
     * 把用户原始问题扩展成多个变体，内置缓存 + 超时 + 重试防护

     expand("怎么追女生") 润色之后的用户问题
     →
     ① 缓存 {"怎么追女生": [变体1, 变体2]} 中有吗？
     有 → 直接返回 ✅
     没有 ↓
     ② 异步调大模型，设置 3 秒超时钟
     超时 → 中断调用，抛异常
     失败 → 睡 500ms → 再试 1 次
     都失败 ↓
     ③ 降级：返回原始问题（只有润色的用户问题 没有变体了，但功能不挂）✅
     成功 → 写缓存 → 返回变体 ✅

     */
    public List<Query> expand(String originalQuery) {
        // ① 缓存命中，直接返回（省一次 LLM 调用）
        if (cache.containsKey(originalQuery)) {
            log.info("【变体扩展命中缓存】：{}", originalQuery);
            return cache.get(originalQuery);
        }

        try {
            // ② 调大模型扩展（内部有超时 + 重试）
            List<Query> queries = expandWithRetry(originalQuery);

            // ③ 写入缓存
            cache.put(originalQuery, queries);
            //log.info("Query 扩展：{} → {} 个变体（已缓存）", originalQuery, queries.size());
            return queries;

        } catch (Exception e) {
            // ④ 全部失败 → 降级为只搜原始问题
            log.warn("Query 扩展完全失败，降级使用原始问题：{}", e.getMessage());
            return List.of(new Query(originalQuery));
        }
    }

    // ===== ② 重试：最多调 2 次，中间隔 500ms =====

    /**
     * 带重试的扩展变体调用：失败后等 500ms 再试 1 次
     */
    private List<Query> expandWithRetry(String query) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return expandWithTimeout(query);
            } catch (Exception e) {
                if (attempt == 2) {
                    throw new RuntimeException(" Query扩展变体：重试 2 次均失败，已降级", e);
                }
                log.warn("Query 变体扩展失败，{}ms 后重试...", 500);

                // 给网络或 DeepSeek 服务喘口气。
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("Query扩展变体重试逻辑异常：所有重试已耗尽但未被正确捕获");
    }

    // ===== ③ 超时：超过 3 秒就放弃，不等大模型 =====

    /**
     * 带超时的扩展调用：异步发请求，超过 3 秒直接放弃
     */
    private List<Query> expandWithTimeout(String query) {
        // CompletableFuture 异步执行：不阻塞主线程 将润色用户的问题进行多方位的变体
        CompletableFuture<List<Query>> future = CompletableFuture.supplyAsync(() ->
                expander.expand(new Query(query))
        );
        try {
            // 最多等 3 秒，超时就抛 TimeoutException
            return future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);                           // 中断正在跑的大模型调用
            throw new RuntimeException("多变体超时（>3 秒）", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
