package com.alumni.wall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 留言墙模块出入参 DTO（全局 Jackson 已配置 snake_case）。
 * 公开列表绝不返回 ip_addr / 手机号等任何个人信息（文档 §4 隐私红线）。
 */
public final class WallDtos {

    private WallDtos() {
    }

    /** POST /wall/messages 提交留言（showName=true 才在公开墙显示真实姓名，缺省/匿名） */
    public record CreateRequest(
            @NotBlank(message = "content 不能为空")
            @Size(max = 120, message = "留言最长 120 字") String content,
            Boolean showName) {
    }

    /** PUT /wall/messages/{id} 编辑本人 pending/rejected 留言（与 Resubmit 复用同一 body） */
    public record UpdateRequest(
            @NotBlank(message = "content 不能为空")
            @Size(max = 120, message = "留言最长 120 字") String content,
            Boolean showName) {
    }

    /** PUT /admin/wall/messages/{id}/review 审核（approve 通过 / reject 驳回需填原因 / hide 取消展示） */
    public record ReviewRequest(
            @NotBlank(message = "action 不能为空")
            @Pattern(regexp = "^(approve|reject|hide)$", message = "action 仅允许 approve/reject/hide") String action,
            @Size(max = 255, message = "note 最长 255 字符") String note) {
    }

    /** 公开列表项（GET /wall/messages，仅 approved；身份字段联查得出；grad_year 拼好"届"） */
    public record PublicItem(Long id, Long userId, String name, String gradYear, String college,
                             String content, boolean isPinned, Instant createdAt) {
    }

    /** 我的留言项（GET /wall/messages/mine，含 pending/rejected 与驳回原因；含软删 deleted 状态） */
    public record MyItem(Long id, String content, Boolean showName, String status, String reviewNote,
                         Instant createdAt, Instant reviewedAt, Instant deletedAt) {
    }

    /** 管理列表项（school_admin；含审核信息与风控 IP） */
    public record AdminItem(Long id, Long userId, String name, String studentId, String content,
                            String status, String reviewNote, boolean isPinned, String ipAddr,
                            Instant createdAt, Instant reviewedAt, Instant deletedAt) {
    }

    /** 分页包装（与 archives 模块 PageResult 格式一致） */
    public record PageResult<T>(List<T> list, long total, int page, int pageSize) {
    }
}
