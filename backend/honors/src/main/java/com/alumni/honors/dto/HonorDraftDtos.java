package com.alumni.honors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 荣誉草稿模块 DTO。
 * 阶段一：人工填写；阶段二：OCR 自动填充 parsedJson 后人工复核。
 */
public final class HonorDraftDtos {

    private HonorDraftDtos() {
    }

    /** POST /honor-drafts 请求体：创建草稿 */
    public record CreateDraftRequest(
            Long attachmentId,

            @NotBlank(message = "kind 不能为空")
            @Pattern(regexp = "^(solo|team)$", message = "kind 仅支持 solo / team")
            String kind,

            @Size(max = 128, message = "title 最长 128 字符")
            String title,

            @Size(max = 32, message = "category 最长 32 字符")
            String category,

            @Size(max = 32, message = "level 最长 32 字符")
            String level,

            LocalDate awardedAt,

            @Size(max = 64, message = "college_name 最长 64 字符")
            String collegeName,

            @Size(max = 512, message = "red_doc_url 最长 512 字符")
            String redDocUrl,

            /** 团队草稿原始成员名单（分隔符：、，,;；/ 空白） */
            String memberNames) {
    }

    /** PATCH /honor-drafts/{id} 请求体：人工修正 */
    public record UpdateDraftRequest(
            @Size(max = 128) String title,
            @Size(max = 32) String category,
            @Size(max = 32) String level,
            LocalDate awardedAt,
            @Size(max = 64) String collegeName,
            @Size(max = 512) String redDocUrl,
            String memberNames) {
    }

    /** POST /honor-drafts/{id}/preview-members 命中结果预览（实时调用） */
    public record PreviewRequest(
            @NotBlank(message = "member_names 不能为空")
            String memberNames) {
    }

    /** 单条预览成员（matched/duplicated/no_account/ambiguous/unmatched） */
    public record PreviewMember(
            String name,
            String studentId,
            Long alumniBaseId,
            Long userId,
            String status,
            String reason) {
    }

    public record PreviewResult(List<PreviewMember> members) {
    }

    /** POST /honor-drafts/{id}/confirm 请求体 */
    public record ConfirmRequest(
            /** 人工逐行修正：name → (可选)user_id。用于歧义/未匹配的人工消歧 */
            List<MemberOverride> overrides,

            /** 是否把 matched 的成员拆成多条个人记录（团队草稿确认时强制 true） */
            boolean splitMembers) {
    }

    public record MemberOverride(String name, Long userId) {
    }

    /** 确认响应：返回生成的 honors.id 列表（团队含 is_team_record 标记） */
    public record ConfirmResult(String teamId, List<HonorItem> honors) {
    }

    public record HonorItem(Long id, Long userId, boolean isTeamRecord, String teamRole) {
    }
}