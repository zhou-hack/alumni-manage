package com.alumni.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 短信验证码服务（Mock 实现）。
 * 6 位数字码，5 分钟有效，验证成功后立即失效。
 * Mock 模式下验证码打印日志并通过 dev_code 回显，供测试前端直接读取；
 * 后续接真实短信服务商时仅需替换本类的 send 实现。
 */
@Service
public class SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "sms:";

    private final StringRedisTemplate redis;
    private final boolean mock;
    private final SecureRandom random = new SecureRandom();

    public SmsCodeService(StringRedisTemplate redis, @Value("${app.sms.mock:true}") boolean mock) {
        this.redis = redis;
        this.mock = mock;
    }

    /**
     * 发送验证码（Mock：不落短信通道，直接写 Redis）。
     *
     * @return mock 模式下返回验证码供响应回显；非 mock 模式返回 null
     */
    public String send(String phone, String scene) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(KEY_PREFIX + scene + ":" + phone, code, CODE_TTL);
        if (mock) {
            log.info("[SMS-MOCK] scene={} phone={} code={}", scene, phone, code);
            return code;
        }
        // TODO: 接入真实短信服务商（阿里云/腾讯云），模板按 scene 区分
        return null;
    }

    /** 校验验证码，通过后立即失效 */
    public boolean verify(String phone, String scene, String code) {
        String key = KEY_PREFIX + scene + ":" + phone;
        String cached = redis.opsForValue().get(key);
        if (cached != null && cached.equals(code)) {
            redis.delete(key);
            return true;
        }
        return false;
    }

    public long getExpireSeconds() {
        return CODE_TTL.toSeconds();
    }
}
