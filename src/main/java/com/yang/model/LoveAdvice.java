package com.yang.model;

import lombok.Data;

/**
 * 恋爱建议模型类
 * 用于接收 AI 的结构化输出
 */
@Data
public class LoveAdvice {

    /**
     * 情感类型：比如 "鼓励"、"建议"、"警告" 等
     */
    private String type;

    /**
     * 建议标题
     */
    private String title;

    /**
     * 具体建议内容
     */
    private String content;

    /**
     * 行动步骤（数组）
     */
    private String[] steps;

    /**
     * 鼓励的话
     */
    private String encouragement;

}