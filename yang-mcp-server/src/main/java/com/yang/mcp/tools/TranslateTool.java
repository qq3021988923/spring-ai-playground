package com.yang.mcp.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.net.URLEncoder; // 导入正确的 URL 编码器
import java.nio.charset.StandardCharsets;

@Slf4j
@Service // 标注为 Spring 服务，由 MCP Server 自动管理
public class TranslateTool {

    /**
     * 将文本翻译成目标语言（基于 MyMemory 免费翻译 API，无需注册 Key）
     */
    @Tool(description = "将文本翻译成目标语言")
    public String translate(
            @ToolParam(description = "要翻译的文本内容") String text,
            @ToolParam(description = "目标语言，如 English, Japanese, Korean, French, German") String targetLanguage
    ) {
        log.info("[MCP] 翻译: {} -> {}", text, targetLanguage);

        try {
            // MyMemory 免费翻译 API，文档：https://mymemory.translated.net/doc/spec.php
            String apiUrl = "https://api.mymemory.translated.net/get";

            // 将中文目标语言转换为 ISO 639-1 代码
            String targetLangCode = convertLanguageToCode(targetLanguage);

            // 请求参数：q=文本内容，langpair=源语言|目标语言（这里固定源语言为中文）
            // 修正：使用 Java 标准库的 URLEncoder 进行编码
            String params = "q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&langpair=zh|" + targetLangCode;

            String responseBody = HttpUtil.get(apiUrl + "?" + params, 5000); // 超时 5 秒

            // 解析 JSON 响应
            JSONObject json = JSONUtil.parseObj(responseBody);
            String translatedText = json.getByPath("responseData.translatedText", String.class);

            if (StrUtil.isNotBlank(translatedText)) {
                log.info("[MCP] MyMemory 翻译成功");
                return "[" + targetLanguage + "] " + translatedText;
            } else {
                // 如果翻译失败，尝试返回匹配片段（MyMemory 匹配库）或原文本
                String matchInfo = json.getByPath("matches[0].segment", String.class);
                String failResult = StrUtil.isNotBlank(matchInfo) ? matchInfo : text;
                log.warn("[MCP] MyMemory 翻译返回空结果，使用匹配结果或原文");
                return "[" + targetLanguage + "] (翻译可能不准确) " + failResult;
            }
        } catch (Exception e) {
            log.error("[MCP] 翻译失败", e);
            return "抱歉，翻译失败：" + e.getMessage();
        }
    }

    /**
     * 将自然语言名称转换为 MyMemory 支持的 ISO 639-1 语言代码
     */
    private String convertLanguageToCode(String language) {
        if (StrUtil.isBlank(language)) {
            return "en";
        }
        return switch (language.toLowerCase().trim()) {
            case "english", "en" -> "en";
            case "japanese", "ja" -> "ja";
            case "korean", "ko" -> "ko";
            case "french", "fr" -> "fr";
            case "german", "de" -> "de";
            case "spanish", "es" -> "es";
            case "portuguese", "pt" -> "pt";
            case "italian", "it" -> "it";
            case "russian", "ru" -> "ru";
            case "arabic", "ar" -> "ar";
            case "chinese", "zh" -> "zh";
            // 如果未匹配，直接返回小写形式（MyMemory 可能接受）
            default -> language.toLowerCase();
        };
    }
}