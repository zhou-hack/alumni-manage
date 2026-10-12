package com.alumni.photo.common;

public final class RequestIdHolder {
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();
    private RequestIdHolder() {}
    public static String get() { return HOLDER.get(); }
    public static void set(String id) { HOLDER.set(id); }
    public static void clear() { HOLDER.remove(); }
}
