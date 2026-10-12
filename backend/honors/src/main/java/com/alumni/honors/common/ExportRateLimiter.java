package com.alumni.honors.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [M03] 荣誉导出限流（内存滑动窗口，重启清零）：单账号 5 次/小时。
 * 与 audit 模块 ExportRateLimiter 同模式（复制而非跨模块依赖，避免动 common 触发全量回归）；
 * 多实例部署时需换集中式限流。
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
