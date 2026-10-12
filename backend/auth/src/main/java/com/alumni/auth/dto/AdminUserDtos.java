package com.alumni.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 人员管理（管理端用户列表）出入参 DTO。
 * 隐私红线：手机号仅出脱敏形式；身份证号（alumni_base.id_card）任何情况下不出参、不可修改。
 */
public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /** 用户列表项（users 联 alumni_base 学籍信息） */
    public record Item(
            long id,
            String uuid,
            String studentId,
            String nickname,
            String realName,
            String phoneMasked,
            String role,
            String college,
            String major,
            Integer gradYear,
            Instant createdAt) {
    }

    /** 分页信封（对齐 API 规范：{list, total, page, page_size}） */
    public record PageResult<T>(List<T> list, long total, int page, int pageSize) {
    }

    /**
     * 编辑用户信息（PATCH /admin/users/{id}）。
     * 全部字段可选：null / 缺省 = 不修改；学号、身份证号不可修改。
     */
    public record UpdateUserRequest(
            @Size(min = 1, max = 64, message = "昵称长度须在 1~64 之间") String nickname,
            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误") String phone,
            @Pattern(regexp = "^(student|alumni|counselor|college_admin|school_admin|system_admin)$",
                    message = "角色必须为平台既有角色") String role,
            @Size(max = 64, message = "学院最长 64 字符") String college,
            @Size(max = 64, message = "专业最长 64 字符") String major,
            @Min(value = 1900, message = "毕业年份须 ≥1900")
            @Max(value = 2100, message = "毕业年份须 ≤2100") Integer gradYear) {
    }

    /** 管理员重置密码（POST /admin/users/{id}/reset-password；new_password 为前端 SHA-256 散列值） */
    public record ResetPasswordRequest(
            @NotBlank(message = "新密码不能为空")
            @Pattern(regexp = "^[0-9a-f]{64}$", message = "密码必须为 SHA-256 散列值") String newPassword) {
    }

    /** 批量重置为默认密码（POST /admin/users/batch-reset-password，仅 system_admin；单次 ≤100） */
    public record BatchResetRequest(
            @jakarta.validation.constraints.NotNull(message = "user_ids 不能为空")
            @Size(min = 1, max = 100, message = "单次最多批量重置 100 个账号")
            List<@jakarta.validation.constraints.NotNull Long> userIds) {
    }

    /** 批量重置结果 */
    public record BatchResetResult(int total, int reset) {
    }

    /**
     * 管理员单个新增账号（POST /admin/users）。
     * 仅允许 student / alumni（管理角色账号走各自通道）；身份证号不采集（桩值为空串，同导员建号先例）；
     * 初始密码为服务端默认密码（AdminUserService.DEFAULT_PASSWORD），创建结果出参告知。
     */
    public record CreateUserRequest(
            @NotBlank(message = "学号不能为空")
            @Size(max = 32, message = "学号最长 32 字符") String studentId,
            @NotBlank(message = "姓名不能为空")
            @Size(max = 64, message = "姓名最长 64 字符") String realName,
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误") String phone,
            @NotBlank(message = "角色不能为空")
            @Pattern(regexp = "^(student|alumni)$", message = "仅允许新增 student / alumni") String role,
            @Size(max = 64, message = "学院最长 64 字符") String college,
            @Size(max = 64, message = "专业最长 64 字符") String major,
            @jakarta.validation.constraints.NotNull(message = "毕业年份不能为空")
            @Min(value = 1900, message = "毕业年份须 ≥1900")
            @Max(value = 2100, message = "毕业年份须 ≤2100") Integer gradYear) {
    }

    /** 单个新增结果（item + 初始密码提示） */
    public record CreateResult(Item item, String initialPassword) {
    }
}
