package com.alumni.counselors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 导员管理模块出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class CounselorDtos {

    private CounselorDtos() {
    }

    /** 管辖范围：届别 ∪ 专业 ∪ 指定学号 三维度并集（至少一项非空，由 Service 校验 SCOPE_REQUIRED） */
    public record ScopePayload(List<Integer> gradYears, List<Long> majorIds, List<String> studentIds) {
    }

    /** POST /counselors 创建导员（password 为前端 SHA-256 散列值；scope 必填校验在 Service 层） */
    public record CreateRequest(
            @NotBlank(message = "real_name 不能为空")
            @Size(max = 64, message = "real_name 最长 64 字符") String realName,
            @NotBlank(message = "phone 不能为空")
            @Size(max = 16, message = "phone 最长 16 字符") String phone,
            @NotNull(message = "college_id 不能为空") Long collegeId,
            @NotBlank(message = "password 不能为空") String password,
            ScopePayload scope) {
    }

    /** PATCH /counselors/{id} 修改（字段均可选，给了才改；scope 给了即全量替换） */
    public record UpdateRequest(
            @Size(max = 64, message = "real_name 最长 64 字符") String realName,
            @Size(max = 16, message = "phone 最长 16 字符") String phone,
            Long collegeId,
            ScopePayload scope) {
    }

    /** POST /counselors/{id}/password/reset（new_password 为前端 SHA-256 散列值） */
    public record ResetPasswordRequest(
            @NotBlank(message = "new_password 不能为空") String newPassword) {
    }

    /** 列表项（手机号已脱敏） */
    public record CounselorItem(Long id, String realName, String phone, Long collegeId, String college,
                                String role, String status, Instant createdAt) {
    }

    /** 详情（账号信息 + 管辖范围聚合对象，手机号已脱敏） */
    public record CounselorDetail(Long id, String realName, String phone, Long collegeId, String college,
                                  String role, String status, ScopePayload scope, Instant createdAt) {
    }

    /** 分页包装（与 archives/announcement 模块 PageResult 格式一致） */
    public record PageResult<T>(List<T> list, long total, int page, int pageSize) {
    }
}
