package com.alumni.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 短时业务令牌（Redis 存储，一次性/限时）：
 * - verify_token：激活核验凭证，10 分钟，绑定学号与目标手机号
 * - reset_token：重置密码凭证，5 分钟，绑定手机号，一次性使用
 */
@Service
public class TokenStoreService {

    private static final Duration VERIFY_TTL = Duration.ofMinutes(10);
    private static final Duration RESET_TTL = Duration.ofMinutes(5);
    private static final String VERIFY_PREFIX = "vtok:";
    private static final String RESET_PREFIX = "rtok:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TokenStoreService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 激活核验上下文 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifyContext(String studentId, String registeredPhone, String targetPhone) {
    }

    public String createVerifyToken(String studentId, String registeredPhone) {
        String token = "v_tok_" + UUID.randomUUID();
        save(VERIFY_PREFIX + token, new VerifyContext(studentId, registeredPhone, registeredPhone), VERIFY_TTL);
        return token;
    }

    public Optional<VerifyContext> getVerifyContext(String token) {
        return load(VERIFY_PREFIX + token, VerifyContext.class);
    }

    /** 更新目标手机号（激活第②步换绑），不刷新 TTL */
    public void updateTargetPhone(String token, VerifyContext context) {
        Long ttl = redis.getExpire(VERIFY_PREFIX + token);
        if (ttl != null && ttl > 0) {
            save(VERIFY_PREFIX + token, context, Duration.ofSeconds(ttl));
        }
    }

    public void deleteVerifyToken(String token) {
        redis.delete(VERIFY_PREFIX + token);
    }

    public String createResetToken(String phone) {
        String token = "rst_tok_" + UUID.randomUUID();
        redis.opsForValue().set(RESET_PREFIX + token, phone, RESET_TTL);
        return token;
    }

    /** 一次性消费：取出即作废 */
    public Optional<String> consumeResetToken(String token) {
        String key = RESET_PREFIX + token;
        String phone = redis.opsForValue().get(key);
        if (phone == null) {
            return Optional.empty();
        }
        redis.delete(key);
        return Optional.of(phone);
    }

    public long getVerifyExpireSeconds() {
        return VERIFY_TTL.toSeconds();
    }

    public long getResetExpireSeconds() {
        return RESET_TTL.toSeconds();
    }

    private void save(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 写入失败", e);
        }
    }

    private <T> Optional<T> load(String key, Class<T> type) {
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
