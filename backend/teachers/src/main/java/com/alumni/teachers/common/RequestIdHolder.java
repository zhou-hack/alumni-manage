package com.alumni.teachers.common;

/**
 * 当前请求的 request_id 持有器，由 RequestIdFilter 在请求进入时写入。
 */
public final class RequestIdHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private RequestIdHolder() {
    }

    public static void set(String requestId) {
        HOLDER.set(requestId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
