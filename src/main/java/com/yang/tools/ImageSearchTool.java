package com.yang.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImageSearchTool {
    // 帮我搜索5张古风图片
    // 去 https://www.pexels.com/api/ 申请免费key
    private static final String API_KEY = " 0XArXQ00daXFFiuL3JhSBfkSsTmWaXJuHo9DGUTDuMs61m5koa4ONoLz";
    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "根据关键词搜索网络图片，返回高清图片链接，支持古风、风景、人物等")
    public String searchImage(
            @ToolParam(description = "搜索关键词，例如：古风、风景、猫咪") String query) {

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", API_KEY);

            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("per_page", 5);

            String resp = HttpUtil.createGet(API_URL)
                    .addHeaders(headers)
                    .form(params)
                    .execute()
                    .body();

            List<String> urls = JSONUtil.parseObj(resp)
                    .getJSONArray("photos")
                    .stream()
                    .map(p -> JSONUtil.parseObj(p).getJSONObject("src").getStr("medium"))
                    .toList();

            return StrUtil.join("\n", urls);
        } catch (Exception e) {
            return "搜索失败：" + e.getMessage();
        }
    }
}