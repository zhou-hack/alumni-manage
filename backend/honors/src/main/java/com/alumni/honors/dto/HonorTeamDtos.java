package com.alumni.honors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * [M03] 团队荣誉出入参 DTO（Jackson SNAKE_CASE 自动映射）。
 * 人名识别结果按四桶返回：created / duplicated / no_account / unmatched。
 */
public final class HonorTeamDtos {

    private HonorTeamDtos() {
    }

    /** POST /honors/team 请求体 */
    public record TeamCreateRequest(
            @NotBlank(message = "title 不能为空")
            @Size(max = 128, message = "title 最长 128 字符")
            String title,

            @NotBlank(message = "category 不能为空")
            @Size(max = 32, message = "category 最长 32 字符")
            String category,

            @NotBlank(message = "level 不能为空")
            @Size(max = 32, message = "level 最长 32 字符")
            String level,

            @NotNull(message = "awarded_at 不能为空")
            LocalDate awardedAt,

            @NotBlank(message = "member_names 不能为空")
            @Size(max = 2000, message = "member_names 最长 2000 字符")
            String memberNames,

            @Size(max = 512, message = "red_doc_url 最长 512 字符")
            String redDocUrl) {
    }

    /** 未匹配人名的拼音候选建议（仅建议，绝不自动匹配） */
    public record Candidate(String studentId, String realName, String college) {
    }

    /** 重复（同人同题同日已存在）桶项 */
    public record DuplicatedItem(String name, String studentId) {
    }

    /** 姓名命中校友库但账号未激活桶项 */
    public record NoAccountItem(String name, String studentId) {
    }

    /** 未匹配/歧义桶项 */
    public record UnmatchedItem(String name, String reason, List<Candidate> candidates) {
    }

    /** POST /honors/team 响应 data */
    public record TeamCreateResult(
            String teamId,
            int created,
            List<DuplicatedItem> duplicated,
            List<NoAccountItem> noAccount,
            List<UnmatchedItem> unmatched) {
    }

    /** 团队详情：成员留证行（含匹配状态与原因） */
    public record MemberItem(String name, String studentId, Long alumniBaseId, Long userId,
                             Long honorId, String matchStatus, String reason) {
    }

    /** 团队详情：同批生成的荣誉摘要 */
    public record TeamHonorItem(Long id, Long userId, String title, String status) {
    }

    /** GET /honors/team/{team_id} 响应 data */
    public record TeamDetailResult(String teamId, String title, String category, String level,
                                   LocalDate awardedAt, String redDocUrl, Long createdBy,
                                   List<MemberItem> members, List<TeamHonorItem> honors) {
    }
}
