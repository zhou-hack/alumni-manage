package com.alumni.auth.service;

/**
 * 注册智能审核建议器（AI 审核预留 SPI）。
 *
 * <p>当前阶段注册审核为纯人工：Mock 实现恒返回「转人工」。未来对接 AI
 * （如基于学校学生数据库自动比对学号/姓名/学籍）时，新增实现类并以
 * {@code app.registration.advisor=ai} 配置启用即可，主流程零改动。</p>
 *
 * <p>建议结果仅作辅助：写入申报单 advisor_note + 审计日志；
 * 即使 autoApprovable=true，本期也不自动通过（规范要求人工兜底）。</p>
 */
public interface RegistrationAdvisor {

    /** 注册申报快照（评估输入，不含密码等敏感信息） */
    record RegistrationApplication(String studentId, String realName, String phone,
                                   String college, String major, Integer gradYear, String note) {
    }

    /**
     * 审核建议。
     *
     * @param autoApprovable 是否满足自动通过条件（本期仅记录，不生效）
     * @param note           建议说明（写入申报单，供人工审核参考）
     */
    record Advice(boolean autoApprovable, String note) {
    }

    /** 对注册申报给出审核建议；实现须保证不抛异常中断主流程（异常时按转人工处理） */
    Advice advise(RegistrationApplication application);
}
