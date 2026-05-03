package com.yang.mcp.tools;

import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Slf4j //  // 注解为 Spring 的服务，会被 MCP Server 自动管理，方便被其他 MCP 工具或主程序通过协议发现
@Service // 天气查询工具
public class WeatherTool {
   /*
   主Agent ：用户问“北京天气怎么样？”。
    Agent发现工具：它看到一个叫 getWeather 的工具，但工具实例不在自己进程里，是通过 MCP 协议连接的。
    发送标准化请求：Agent 的 MCP 客户端将调用请求（函数名、参数 city="北京"）序列化为 JSON，通过标准输入（stdin）
    发送给独立的 yang-mcp-server 子进程。
    MCP Server处理：yang-mcp-server 进程从自己的标准输出（stdout） 收到请求，找到 WeatherTool.getWeather 方法，
    执行 http://wttr.in/... 的 HTTP 调用。
    返回标准化响应：MCP Server 将天气结果序列化为 JSON，写回自己的标准输出（stdout）。
    Agent接收结果：主 Agent 从子进程的标准输入（stdin） 读到响应，解析出天气信息，继续生成最终回答。*/
    private static final String API_URL = "https://wttr.in/";

    //   // @Tool 注解将普通方法变为 AI 可调用的工具。描述信息对 AI 调用至关重要。
    @Tool(description = "查询指定城市的实时天气情况")
    public String getWeather(@ToolParam(description = "城市名称，如 北京、上海") String city) {
        log.info("[MCP] 查询 {} 天气", city);

        try {
            //  // 调用 wttr.in 免费天气 API，不需要 API Key
            String url = API_URL + city + "?format=%C+%t+%h+%w&lang=zh";
            String response = HttpUtil.get(url, 5000); //  // 使用 Hutool 工具发起 HTTP GET 请求，5秒超时

            return city + " 当前天气：" + response.trim();
        } catch (Exception e) {
            log.error("查询天气失败", e);
            return "抱歉，查询" + city + "天气失败：" + e.getMessage();
        }
    }

}