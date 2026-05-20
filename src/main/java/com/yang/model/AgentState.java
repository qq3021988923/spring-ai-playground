package com.yang.model;

/**
 * 智能体状态枚举
 * 核心：控制Agent的生命周期（空闲→运行→结束/异常）
 */
public enum AgentState {
    IDLE,     // 空闲状态（初始状态，可接收新任务）
    RUNNING,  // 运行中（正在执行多步任务）
    FINISHED, // 执行完成（任务正常结束）
    ERROR     // 执行异常（任务出错）
}