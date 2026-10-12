package com.alumni.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 注册审核建议器 · Mock 实现（默认）：恒返回「转人工」。
 * AI 实现就绪后以 app.registration.advisor=ai 切换，本 Bean 自动失效。
 */
@Component
@ConditionalOnProperty(name = "app.registration.advisor", havingValue = "mock", matchIfMissing = true)
public class MockRegistrationAdvisor implements RegistrationAdvisor {

    @Override
    public Advice advise(RegistrationApplication application) {
        return new Advice(false, "Mock：未接入智能核验，转人工审核");
    }
}
