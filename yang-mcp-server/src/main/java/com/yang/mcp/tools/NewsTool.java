package com.yang.mcp.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 新闻搜索工具（基于 NewsAPI.org 真实 API）
 * 使用前请访问 https://newsapi.org/register 注册免费账号，获取 API Key
 * 免费账号每天 100 次请求，足够开发测试使用
 */
@Slf4j
@Service
public class NewsTool {

    // 替换成你自己的 NewsAPI Key（免费注册获得）
    private static final String NEWS_API_KEY = "81c5dc3c286a4f96b1302f4ae3ea90f6";

    // NewsAPI 端点
    private static final String NEWS_API_URL = "https://newsapi.org/v2/top-headlines";

    @Tool(description = "搜索最新的新闻资讯")
    public String searchNews(
            @ToolParam(description = "搜索关键词，如 科技、体育、财经") String keyword,
            @ToolParam(description = "返回条数，默认5条") int limit
    ) {
        log.info("[MCP] 搜索新闻: {}, 条数={}", keyword, limit);

        try {
            // 对关键词进行 URL 编码，确保含有特殊字符时 API 仍正确解析
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            // 构造请求 URL：国家为中国，类别为关键词的小写形式，pageSize 为返回条数
            String url = String.format(
                    "%s?country=cn&category=%s&pageSize=%d&apiKey=%s",
                    NEWS_API_URL,
                    keyword.toLowerCase(),
                    Math.min(limit, 10),   // NewsAPI 最多允许 100，我们限制在 10 条以内
                    NEWS_API_KEY
            );

            // 发送 HTTP GET 请求，超时 5 秒
            String responseBody = HttpUtil.get(url, 5000);

            // 解析 JSON 响应
            JSONObject json = JSONUtil.parseObj(responseBody);

            // 检查 API 返回状态
            String status = json.getStr("status");
            if (!"ok".equals(status)) {
                String errorMsg = json.getStr("message", "未知错误");
                log.warn("[MCP] NewsAPI 返回错误: {}", errorMsg);
                return "抱歉，新闻搜索失败：" + errorMsg;
            }

            // 提取文章列表
            JSONArray articles = json.getJSONArray("articles");
            if (articles == null || articles.isEmpty()) {
                return "未找到关于「" + keyword + "」的新闻。";
            }

            // 取前 limit 条新闻的标题，拼成一行一条
            List<String> titles = articles.stream()
                    .limit(limit)
                    .map(article -> {
                        JSONObject a = (JSONObject) article;
                        return a.getStr("title", "无标题");
                    })
                    .collect(Collectors.toList());

            return "关于「" + keyword + "」的最新新闻：\n" + String.join("\n", titles);
        } catch (Exception e) {
            log.error("[MCP] 新闻搜索失败", e);
            return "抱歉，新闻搜索暂不可用：" + e.getMessage();
        }
    }
}