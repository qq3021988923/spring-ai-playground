package com.yang.config;

import com.yang.rag.LoveDocumentLoader;
import com.yang.tools.MyTools;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class ToolConfig {

    @Bean
    public MyTools myTools() {
        return new MyTools();
    }

    @Bean
    public ToolCallback[] toolCallbacks(MyTools tools, LoveDocumentLoader documentLoader) {

        // 工具1：获取当前时间（无参数）
        ToolCallback getTime = FunctionToolCallback
                .builder("getCurrentTime", (Void v) -> tools.getCurrentTime())
                .description("获取当前系统时间")
                .inputType(Void.class)
                .build();

        // 工具2：查询用户信息（通过工号）
        ToolCallback queryUser = FunctionToolCallback
                .builder("queryUserInfo", (Map<String, Object> params) -> {
                    String userId = (String) params.get("userId");
                    return tools.queryUserInfo(userId);
                })
                .description("根据工号查询用户信息")
                .inputType(Map.class)
                .inputSchema("""
                    {
                        "type": "object",
                        "properties": {
                            "userId": {
                                "type": "string",
                                "description": "用户工号，例如 1001"
                            }
                        },
                        "required": ["userId"]
                    }
                    """)
                .build();

        // 工具3：计算器（支持加减乘除）
        ToolCallback calculator = FunctionToolCallback
                .builder("calculator", (Map<String, Object> params) -> {
                    double num1 = Double.parseDouble(params.get("num1").toString());
                    String operator = (String) params.get("operator");
                    double num2 = Double.parseDouble(params.get("num2").toString());
                    return tools.calculator(num1, operator, num2);
                })
                .description("执行简单的数学计算，支持 + - * /")
                .inputType(Map.class)
                .inputSchema("""
                    {
                        "type": "object",
                        "properties": {
                            "num1": { "type": "number", "description": "第一个数字" },
                            "operator": { "type": "string", "description": "运算符，支持 + - * /" },
                            "num2": { "type": "number", "description": "第二个数字" }
                        },
                        "required": ["num1", "operator", "num2"]
                    }
                    """)
                .build();

        // 工具4：搜索恋爱知识库（RAG）
        ToolCallback searchKnowledge = FunctionToolCallback
                .builder("searchLoveKnowledge", (String query) -> {
                    List<Document> docs = documentLoader.search(query);
                    return docs.stream()
                            .map(Document::getText)
                            .collect(Collectors.joining("\n---\n"));
                })
                .description("在恋爱知识库中搜索相关建议和心得，输入问题关键词，返回相关知识片段")
                .inputType(String.class)
                .build();

        // 合并所有工具并返回
        return new ToolCallback[]{getTime, queryUser, calculator, searchKnowledge};
    }
}