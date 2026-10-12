package com.alumni.audit.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审计导出限流（内存滑动窗口，重启清零）：单账号 5 次/小时。
 * 思路与 auth 模块 RateLimitService 一致（进程内内存限流），
 * 本类采用精确滑动窗口（Deque 记录每次导出时刻），多实例部署时需换集中式限流。
 */
@Component
public class ExportRateLimiter {

    static final int MAX_PER_HOUR = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<Long, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * @return true 放行并记录一次；false 触发限流（一小时内已达 5 次）
     */
    public synchronized boolean tryAcquire(long userId) {
        Instant now = Instant.now();
        Instant threshold = now.minus(WINDOW);
        Deque<Instant> deque = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        while (!deque.isEmpty() && deque.peekFirst().isBefore(threshold)) {
            deque.pollFirst();
        }
        if (deque.size() >= MAX_PER_HOUR) {
            return false;
        }
        deque.addLast(now);
        return true;
    }
}
