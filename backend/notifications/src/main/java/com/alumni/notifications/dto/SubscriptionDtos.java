package com.alumni.notifications.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 外部消息订阅出入参 DTO（全局 Jackson 已配置 snake_case）。
 * scenes 取值 review_result / announcement（合法性在 Service 校验）；
 * enabled 缺省按 false 处理（默认全关，最小打扰原则）。
 */
public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    /** sms 渠道配置（手机号沿用账号绑定号，不单独存地址） */
    public record SmsConfig(Boolean enabled, List<String> scenes) {
    }

    /** email 渠道配置 */
    public record EmailConfig(Boolean enabled, String address, List<String> scenes) {
    }

    /** PUT /notification-subscriptions 请求（sms / email 均须提交，upsert 即时生效） */
    public record UpdateRequest(
            @NotNull(message = "sms 配置不能为空") @Valid SmsConfig sms,
            @NotNull(message = "email 配置不能为空") @Valid EmailConfig email) {
    }

    /** GET /notification-subscriptions/my 响应（无记录时默认全关、scenes 空数组） */
    public record MyResponse(SmsConfig sms, EmailConfig email) {
    }
}
