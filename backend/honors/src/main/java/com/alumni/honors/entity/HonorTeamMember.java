package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * [M03] 团队荣誉成员匹配留证：团队提交时全部人名（含未匹配/歧义/无账号）逐条入表，
 * 作为人名识别结果与后续人工补录的依据。created_at 由数据库管理。
 */
@Getter
@Setter
@Entity(name = "honorsHonorTeamMember")
@Table(name = "honor_team_members")
public class HonorTeamMember {

    /** 匹配状态：已匹配并生成荣誉 */
    public static final String STATUS_MATCHED = "matched";
    /** 匹配成功但同人同题同日荣誉已存在（防重跳过） */
    public static final String STATUS_DUPLICATED = "duplicated";
    /** 姓名命中校友库但账号未激活（无法挂载荣誉） */
    public static final String STATUS_NO_ACCOUNT = "no_account";
    /** 同名多条无法消歧（不自动建荣誉，防错挂他人） */
    public static final String STATUS_AMBIGUOUS = "ambiguous";
    /** 未命中校友库（reason 附拼音候选建议） */
    public static final String STATUS_UNMATCHED = "unmatched";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 团队批次 UUID（关联 honors.team_id） */
    @Column(name = "team_id", nullable = false, length = 36)
    private String teamId;

    /** 解析出的原始人名 */
    @Column(name = "name_raw", nullable = false, length = 64)
    private String nameRaw;

    /** 命中校友库 alumni_base.id（未匹配为 NULL） */
    @Column(name = "alumni_base_id")
    private Long alumniBaseId;

    /** 命中已激活账号 users.id */
    @Column(name = "user_id")
    private Long userId;

    /** 生成的荣誉 honors.id（matched 才有） */
    @Column(name = "honor_id")
    private Long honorId;

    /** matched/duplicated/no_account/ambiguous/unmatched */
    @Column(name = "match_status", nullable = false, length = 16)
    private String matchStatus;

    /** 未匹配/歧义原因（ambiguous 存候选摘要） */
    @Column(name = "reason", length = 255)
    private String reason;

    /** 提交人 users.id（团队详情可见性校验用） */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
