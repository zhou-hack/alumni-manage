package com.alumni.common.security;

/**
 * 已认证用户主体（从 access_token 解析，auth 模块签发）。
 *
 * <p>字段说明：
 * <ul>
 *   <li>userId — users.id（账号主键）</li>
 *   <li>studentId — users.student_id（学号）</li>
 *   <li>role — student / alumni / counselor / college_admin / school_admin / system_admin</li>
 *   <li>alumniBaseId — users.alumni_base_id（校友库关联 ID；非校友端可能为 null）</li>
 * </ul>
 *
 * <p>V18 扩展：alumniBaseId 用于「校友本人编辑自己档案」端点的 owner 校验。</p>
 */
public record AuthPrincipal(Long userId, String studentId, String role, Long alumniBaseId) {

    /** 审核角色 = counselor / college_admin / school_admin / system_admin（P1 起导员参与 counselor 阶段审核，系统管理员为全量超集） */
    public boolean isReviewer() {
        return "counselor".equals(role) || "college_admin".equals(role)
                || "school_admin".equals(role) || "system_admin".equals(role);
    }

    /** 校友端（student/alumni 同级，仅毕业状态不同） */
    public boolean isAlumniSide() {
        return "student".equals(role) || "alumni".equals(role);
    }
}
