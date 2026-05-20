package com.yang.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct模式智能体（思考 → 行动）
 * 继承BaseAgent，拆分核心逻辑：
 * 1. think()：思考是否需要调用工具/执行下一步
 * 2. act()：执行具体操作（调用工具/生成答案）
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考方法
     * @return true=需要执行行动，false=直接结束
     */
    public abstract boolean think();

    /**
     * 行动方法
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 实现父类的step()方法
     * 统一流程：先思考 → 再决定是否行动
     */
    @Override
    public String step() {
        try {
            // 第一步：思考
            boolean shouldAct = think();
            // 不需要行动：直接返回结果
            if (!shouldAct) {
                return "思考完成，无需执行任何操作";
            }
            // 需要行动：执行行动
            return act();
        } catch (Exception e) {
            log.error("步骤执行失败", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }
}