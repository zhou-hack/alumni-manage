package com.alumni.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 停权账号即时拦截器（《[定稿]API规范P1》5.2：停权后所有已签发 Token 即刻作废）。
 *
 * <p>实现：停权时写入 Redis 键 {@code auth:suspended:{userId}}（无 TTL，恢复时删除）；
 * JwtAuthFilter 每请求校验，命中即视为未认证（受保护端点返回 401/403）。
 * 配合 refresh_token 全量吊销，达到"即刻作废"语义。</p>
 *
 * <p>Redis 不可用时降级为不拦截（模块独立调试无 Redis 场景），登录侧仍由 auth 模块
 * 检查 users.status 阻断新登录。</p>
 */
@Component
public class SuspendedUserStore {

    private static final String KEY_PREFIX = "auth:suspended:";

    private final StringRedisTemplate redis;

    public SuspendedUserStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    /** 标记停权（suspend / counselor disable / handover 原账号） */
    public void suspend(Long userId) {
        if (redis != null && userId != null) {
            redis.opsForValue().set(KEY_PREFIX + userId, "1");
        }
    }

    /** 解除停权（resume / counselor enable） */
    public void resume(Long userId) {
        if (redis != null && userId != null) {
            redis.delete(KEY_PREFIX + userId);
        }
    }

    /** 是否已停权（Redis 不可用时返回 false，降级放行） */
    public boolean isSuspended(Long userId) {
        if (redis == null || userId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + userId));
        } catch (Exception e) {
            return false;
        }
    }
}
