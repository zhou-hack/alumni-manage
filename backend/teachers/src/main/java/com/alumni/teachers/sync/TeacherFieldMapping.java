package com.alumni.teachers.sync;

import java.util.Map;

/**
 * 内网接口字段映射：JSON key 集中一处，内网接口字段名变动只改这里。
 */
public final class TeacherFieldMapping {

    public static final String TEACHER_NO = "teacher_no";
    public static final String NAME = "name";
    public static final String COLLEGE = "college";
    public static final String MAJOR = "major";
    public static final String TITLE = "title";
    public static final String EXTERNAL_ID = "external_id";

    private TeacherFieldMapping() {
    }

    /** 同步用教师记录（已从原始 Map 抽取并 trim） */
    public record TeacherRecord(String teacherNo, String name, String college,
                                String major, String title, String externalId) {

        /** 从原始 Map 抽取；工号/姓名缺失返回 null（同步流程跳过该行） */
        public static TeacherRecord from(Map<String, Object> row) {
            String teacherNo = text(row.get(TEACHER_NO));
            String name = text(row.get(NAME));
            if (teacherNo == null || name == null) {
                return null;
            }
            return new TeacherRecord(teacherNo, name,
                    text(row.get(COLLEGE)), text(row.get(MAJOR)),
                    text(row.get(TITLE)), text(row.get(EXTERNAL_ID)));
        }

        private static String text(Object v) {
            if (v == null) {
                return null;
            }
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : s;
        }
    }
}
