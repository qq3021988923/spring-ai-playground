package com.yang.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class TerminateTool {

    // 让ai停止思考
    @Tool(description = "当任务已完成或无法继续时，调用此工具终止交互")
    public String doTerminate() {
        return "任务结束";
    }
}