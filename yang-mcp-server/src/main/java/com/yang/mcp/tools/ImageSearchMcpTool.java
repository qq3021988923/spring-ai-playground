package com.yang.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool; // ✅ 正确注解（统一标准）
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ImageSearchMcpTool {

    @Value("${pexels.api-key}")
    private String pexelsApiKey;

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    // ✅ 用官方标准 @Tool，废弃 @McpTool
    @Tool(description = "根据关键词搜索网络图片，返回多张高清图片URL链接，支持古风、风景、动物等关键词")
    public String searchImage(String query) {
        if (StrUtil.isBlank(query)) {
            return "搜索失败：关键词不能为空";
        }

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", pexelsApiKey);

            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("per_page", 5);

            String responseBody = HttpUtil.createGet(PEXELS_API_URL)
                    .addHeaders(headers)
                    .form(params)
                    .execute()
                    .body();

            List<String> imageUrlList = JSONUtil.parseObj(responseBody)
                    .getJSONArray("photos")
                    .stream()
                    .map(photoObj -> (JSONObject) photoObj)
                    .map(photoObj -> photoObj.getJSONObject("src"))
                    .map(srcObj -> srcObj.getStr("medium"))
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());

            if (imageUrlList.isEmpty()) {
                return StrUtil.format("未搜索到【{}】相关图片", query);
            }

            return StrUtil.join("\n", imageUrlList);

        } catch (Exception e) {
            return StrUtil.format("图片搜索失败：{}", e.getMessage());
        }
    }
}