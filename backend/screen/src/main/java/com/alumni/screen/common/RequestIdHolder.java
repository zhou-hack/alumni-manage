package com.alumni.screen.common;

/**
 * 请求 ID 线程本地持有（由 RequestIdFilter 写入/清理）。
 * 模块化单体内由 common 的全局异常处理器读取，screen 模块同名复用。
 */
public final class RequestIdHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private RequestIdHolder() {
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void set(String id) {
        HOLDER.set(id);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
