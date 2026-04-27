package com.yang.config;


import com.yang.tools.MyTools;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 工具配置类
 * 注册我们的自定义工具，让 Spring AI 可以使用
 */
@Configuration
public class ToolConfig {


    @Bean
    public MyTools myTools() {   // 保留 MyTools 实例，供 FunctionToolCallback 使用
        return new MyTools();
    }

    @Bean
    public ToolCallback[] toolCallbacks(MyTools tools) {
        // 工具1：获取当前时间（无参数）
        ToolCallback getTime = FunctionToolCallback
                .builder("getCurrentTime", (Void v) -> tools.getCurrentTime())
                .description("获取当前系统时间")
                .inputType(Void.class)
                .build();

        // 工具2：查询用户信息
        ToolCallback queryUser = FunctionToolCallback
                .builder("queryUserInfo", (java.util.Map<String, Object> params) -> {
                    String userId = (String) params.get("userId");
                    return tools.queryUserInfo(userId);
                })
                .description("根据工号查询用户信息")
                .inputType(java.util.Map.class)
                .inputSchema("{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\",\"description\":\"用户工号，例如 1001\"}},\"required\":[\"userId\"]}")
                .build();

        // 工具3：计算器
        ToolCallback calculator = FunctionToolCallback
                .builder("calculator", (java.util.Map<String, Object> params) -> {
                    double num1 = Double.parseDouble(params.get("num1").toString());
                    String operator = (String) params.get("operator");
                    double num2 = Double.parseDouble(params.get("num2").toString());
                    return tools.calculator(num1, operator, num2);
                })
                .description("执行简单的数学计算")
                .inputType(java.util.Map.class)
                .inputSchema("{\"type\":\"object\",\"properties\":{\"num1\":{\"type\":\"number\",\"description\":\"第一个数字\"},\"operator\":{\"type\":\"string\",\"description\":\"运算符，支持 + - * /\"},\"num2\":{\"type\":\"number\",\"description\":\"第二个数字\"}},\"required\":[\"num1\",\"operator\",\"num2\"]}")
                .build();

        return new ToolCallback[]{getTime, queryUser, calculator};
    }

}