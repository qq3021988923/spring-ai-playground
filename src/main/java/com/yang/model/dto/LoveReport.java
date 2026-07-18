package com.yang.model.dto;

import lombok.Data;
import java.util.List;

/**
 * 恋爱分析报告 — 结构化输出 DTO
 *
 * <p><b>作用：</b></p>
 * 让大模型按固定 JSON 格式输出，不再是一段自由文本。
 * Spring AI 拿到大模型返回的 JSON 后，自动反序列化成本类对象。
 *
 * <p><b>面试要点：</b></p>
 * "大模型输出不可控，我通过 DTO 字段约束 + System Prompt 描述格式，
 *  让 ChatClient.call().entity() 自动完成 JSON → Java 的反序列化，
 *  把大模型变成了可控的结构化 API。"
 */
@Data
public class LoveReport {

    /** 用户问题的核心诊断 */
    private String problem;

    /** 深度分析（情感心理学角度） */
    private String analysis;

    /** 3-5 条可执行的建议 */
    private List<String> suggestions;

    /** 7 天实操计划（每天一件小事） */
    private List<String> actionPlan;

    /** 风险等级：低 / 中 / 高 */
    private String riskLevel;

    /** 暖心结语 */
    private String encouragement;
}
