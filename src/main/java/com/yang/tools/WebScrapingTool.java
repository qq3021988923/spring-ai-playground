package com.yang.tools;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/*
网页抓取工具
作用 联网搜索能力✅ 抓取文章、新闻、公告✅ 抓取商品信息✅ 抓取公共数据
* */
@Component
public class WebScrapingTool {
    // 帮我抓取这个网页的内容：https://www.runoob.com   https://www.w3school.com.cn/html/index.asp

    //普通网站 → 随便爬，工具很强
    //大厂大站 → 爬不到，正常现象
    //隐私 / 付费 → 不能爬，合规第一

    // 最大重试次数
    private static final int MAX_RETRIES = 2;

    @Tool(description = "抓取指定网页的纯文本内容，用于获取网页信息、文章、新闻、公告等")
    public String scrapeWebPage(
            @ToolParam(description = "需要抓取的网页完整URL，必须以 http 或 https 开头") String url) {

        // 重试机制
        for (int i = 0; i <= MAX_RETRIES; i++) {
            try {
                // 顶级浏览器伪装，最强反爬绕过
                Connection connection = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .followRedirects(true)  // 允许自动重定向
                        .ignoreHttpErrors(true) // 忽略状态码错误
                        .timeout(10000);        // 10秒超时

                Document document = connection.get();
                String content = document.body().text().trim();

                // 空内容判断
                if (content.isBlank()) {
                    return "抓取失败：网站返回空内容（反爬拦截/无权限访问）";
                }

                return "【网页抓取成功】\n" + content;

            } catch (Exception e) {
                // 最后一次重试失败，返回错误
                if (i == MAX_RETRIES) {
                    return "抓取网页失败：" + e.getMessage() + "（可能被网站反爬拦截）";
                }
                // 等待1秒重试
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }

        return "抓取网页失败：多次重试后仍未成功";
    }
}