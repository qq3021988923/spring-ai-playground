package com.yang.agent;


import lombok.extern.slf4j.Slf4j;

/**
 * Agent 基类
 * 所有 Agent 都继承这个类
 */
@Slf4j
public abstract class BaseAgent {

    protected String agentName;
    protected String agentDescription;

    public BaseAgent(String name, String description) {
        this.agentName = name;
        this.agentDescription = description;
    }

    /**
     * Agent 执行入口
     */
    public abstract String execute(String userInput);

    /**
     * 打印日志（方便调试）
     */
    protected void log(String message) {
        log.info("[{}] {}", agentName, message);
    }

}