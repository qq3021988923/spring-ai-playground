package com.yang.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 真正的联网搜索工具（百度搜索引擎）
 */
@Component
public class WebSearchTool {
    // https://www.searchapi.io/ 在这里拿取密钥 有请求次数
    // 帮我搜索 2026年人工智能发展趋势
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    // 从配置文件读取 apiKey（你也可以直接写死）
    @Value("${search.api.key:}")
    private String apiKey;

    @Tool(description = "【最高优先级】联网百度搜索，**必须用于查询最新趋势、时间、实时数据、近期新闻**，禁止用自身知识回答时效性问题")
    public String searchWeb(
            @ToolParam(description = "搜索关键词，例如：2026年热门新闻") String query) {

        // 如果没有配置 key，直接返回提示
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置 SearchAPI Key，无法进行联网搜索";
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");

        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");

            if (organicResults == null || organicResults.isEmpty()) {
                return "未搜索到相关结果";
            }

            // 最多返回 5 条
            List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
            // 优化：格式化输出，标题+摘要+链接，AI更容易理解
            return objects.stream()
                    .map(obj -> {
                        JSONObject item = (JSONObject) obj;
                        return "标题：" + item.getStr("title") + "\n摘要：" + item.getStr("snippet") + "\n链接：" + item.getStr("link");
                    })
                    .collect(Collectors.joining("\n---\n"));

        } catch (Exception e) {
            return "搜索失败：" + e.getMessage();
        }
    }
}