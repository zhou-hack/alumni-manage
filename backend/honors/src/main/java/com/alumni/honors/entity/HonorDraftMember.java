package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * [M03 扩展] 草稿阶段的人名匹配留证。人工录入时按 HonorMatchService 实时预匹配，
 * 会员支持手动选择候选（status='manual'）。草稿确认后转写 honor_team_members。
 */
@Getter
@Setter
@Entity(name = "honorsHonorDraftMember")
@Table(name = "honor_draft_members")
public class HonorDraftMember {

    public static final String STATUS_MATCHED = "matched";
    public static final String STATUS_DUPLICATED = "duplicated";
    public static final String STATUS_NO_ACCOUNT = "no_account";
    public static final String STATUS_AMBIGUOUS = "ambiguous";
    public static final String STATUS_UNMATCHED = "unmatched";
    /** 人工在确认页手动选择候选 → 转写 honors.user_id */
    public static final String STATUS_MANUAL = "manual";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "name_raw", nullable = false, length = 64)
    private String nameRaw;

    @Column(name = "alumni_base_id")
    private Long alumniBaseId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "match_status", nullable = false, length = 16)
    private String matchStatus;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}