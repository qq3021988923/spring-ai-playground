//package com.yang.tools;
//
//import org.springframework.ai.tool.annotation.Tool;
//import org.springframework.ai.tool.annotation.ToolParam;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * 自定义工具类
// * 学习第5步：AI 工具调用（Function Calling）
// * @Tool：工具的 “使用说明书”。AI 会根据用户的问题，自己判断：“这个问题需要我用哪个工具吗？用哪个？怎么填参数？”
// */
//public class MyTools {
//
//    // 模拟一个用户数据库
//    private static final Map<String, String> userDatabase = new HashMap<>();
//    static {
//        userDatabase.put("1001", "张三 - 产品经理 - 入职日期：2023-01-15");
//        userDatabase.put("1002", "李四 - 开发工程师 - 入职日期：2022-08-20");
//        userDatabase.put("1003", "王五 - 设计师 - 入职日期：2023-06-10");
//    }
//
//    /**
//     * 工具1：获取当前时间
//     */
//    @Tool(description = "获取当前系统时间")
//    public String getCurrentTime() {
//        LocalDateTime now = LocalDateTime.now();
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
//        String result = "当前时间是：" + now.format(formatter);
//        System.out.println("[工具调用] getCurrentTime() -> " + result);
//        return result;
//    }
//
//    /**
//     * 工具2：查询用户信息
//     */
//    @Tool(description = "根据工号查询用户信息")
//    public String queryUserInfo(@ToolParam(description = "用户工号，例如 1001") String userId) {
//        String result = userDatabase.getOrDefault(userId, "未找到工号为 " + userId + " 的用户");
//        System.out.println("[工具调用] queryUserInfo(" + userId + ") -> " + result);
//        return result;
//    }
//
//    /**
//     * 工具3：计算器（简单计算）
//     */
//    @Tool(description = "执行简单的数学计算")
//    public String calculator(
//            @ToolParam(description = "第一个数字") double num1,
//            @ToolParam(description = "运算符，支持 + - * /") String operator,
//            @ToolParam(description = "第二个数字") double num2) {
//
//        double result;
//        String opStr;
//        switch (operator) {
//            case "+":
//                result = num1 + num2;
//                opStr = "加";
//                break;
//            case "-":
//                result = num1 - num2;
//                opStr = "减";
//                break;
//            case "*":
//                result = num1 * num2;
//                opStr = "乘";
//                break;
//            case "/":
//                if (num2 == 0) {
//                    return "错误：除数不能为0";
//                }
//                result = num1 / num2;
//                opStr = "除以";
//                break;
//            default:
//                return "错误：不支持的运算符 " + operator;
//        }
//
//        String resultStr = num1 + " " + opStr + " " + num2 + " = " + result;
//        System.out.println("[工具调用] calculator(" + num1 + ", " + operator + ", " + num2 + ") -> " + resultStr);
//        return resultStr;
//    }
//
//}