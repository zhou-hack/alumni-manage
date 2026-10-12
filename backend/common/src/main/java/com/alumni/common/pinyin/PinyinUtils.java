package com.alumni.common.pinyin;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;

/**
 * 拼音工具（《[定稿]API规范P1》3.3：MySQL 预生成拼音列，全拼 + 首字母，不引入 ES）。
 *
 * <p>写入侧（alumni_base.real_name / archives.title / honors.title / dicts.label 等索引字段）
 * 在新增/修改时生成两列：</p>
 * <ul>
 *   <li>全拼列：小写、无声调、无分隔（"张三" → "zhangsan"）</li>
 *   <li>首字母列：每字拼音首字母（"张三" → "zs"）</li>
 * </ul>
 * <p>非汉字字符（字母/数字）原样保留转小写；多音字取第一个读音（同音字如 张/章 自然命中）。</p>
 */
public final class PinyinUtils {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private PinyinUtils() {
    }

    /** 全拼（小写无分隔），如 "张三" → "zhangsan"；纯英文原样小写 */
    public static String toFullPinyin(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            sb.append(pinyinOf(c));
        }
        return sb.toString();
    }

    /** 首字母缩写，如 "张三" → "zs"；非汉字取字符本身（字母转小写） */
    public static String toAbbr(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            String py = pinyinOf(c);
            if (!py.isEmpty()) {
                sb.append(py.charAt(0));
            }
        }
        return sb.toString();
    }

    private static String pinyinOf(char c) {
        if (Character.toString(c).matches("[\\u4E00-\\u9FA5]")) {
            try {
                String[] readings = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                if (readings != null && readings.length > 0) {
                    return readings[0];
                }
            } catch (Exception e) {
                // 转换失败按原字符处理
            }
            return String.valueOf(c);
        }
        return String.valueOf(Character.toLowerCase(c));
    }
}
