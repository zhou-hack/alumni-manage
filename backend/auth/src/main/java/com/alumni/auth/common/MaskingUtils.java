package com.alumni.auth.common;

/**
 * 出参脱敏工具（API 规范 1.6：手机号/身份证出参一律服务端脱敏）。
 */
public final class MaskingUtils {

    private MaskingUtils() {
    }

    /** 手机号脱敏：138****1234 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
