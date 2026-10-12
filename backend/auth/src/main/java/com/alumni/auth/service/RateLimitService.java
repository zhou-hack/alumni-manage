package com.alumni.auth.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单内存限流（固定窗口计数器，重启清零）：
 * - 激活核验：单 IP 10 次/小时，单学号 5 次/天
 * - 发验证码：同一手机号 60 秒内仅允许请求一次 + 单 IP 10 个不同手机号/小时（同号不计，防换号绕过）
 */
@Service
public class RateLimitService {

    private record Window(long startMillis, AtomicInteger count) {
    }

    private record DistinctWindow(long startMillis, Set<String> values) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Map<String, DistinctWindow> distinctWindows = new ConcurrentHashMap<>();

    /**
     * @return true 放行；false 触发限流
     */
    public synchronized boolean tryAcquire(String key, int maxCount, long windowSeconds) {
        long now = System.currentTimeMillis();
        Window window = windows.get(key);
        if (window == null || now - window.startMillis() >= windowSeconds * 1000) {
            windows.put(key, new Window(now, new AtomicInteger(1)));
            evictIfNeeded(now);
            return true;
        }
        return window.count().incrementAndGet() <= maxCount;
    }

    /**
     * 去重限流：窗口内不同 value 的数量超过 maxDistinct 才拦截。
     * 相同 value 重复出现不占用配额（同一手机号不受 IP 限制）。
     *
     * @return true 放行；false 触发限流
     */
    public synchronized boolean tryAcquireDistinct(String key, String value, int maxDistinct, long windowSeconds) {
        long now = System.currentTimeMillis();
        DistinctWindow window = distinctWindows.get(key);
        if (window == null || now - window.startMillis() >= windowSeconds * 1000) {
            Set<String> values = new HashSet<>();
            values.add(value);
            distinctWindows.put(key, new DistinctWindow(now, values));
            evictIfNeeded(now);
            return true;
        }
        if (window.values().contains(value)) {
            return true;
        }
        if (window.values().size() >= maxDistinct) {
            return false;
        }
        window.values().add(value);
        return true;
    }

    /** 防止长时间运行后 Map 膨胀：超过容量时清理过期窗口 */
    private void evictIfNeeded(long now) {
        if (windows.size() + distinctWindows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() > 24 * 3600 * 1000);
            distinctWindows.entrySet().removeIf(e -> now - e.getValue().startMillis() > 24 * 3600 * 1000);
        }
    }
}
