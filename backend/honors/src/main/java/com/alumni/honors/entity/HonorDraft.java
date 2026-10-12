package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * [M03 扩展] 荣誉证书解析草稿。阶段一：人工录入；阶段二：OCR 自动填充 parsed_json。
 * 确认后转写为 honors + honor_team_members 正式记录。
 */
@Getter
@Setter
@Entity(name = "honorsHonorDraft")
@Table(name = "honor_drafts")
public class HonorDraft {

    public static final String KIND_SOLO = "solo";
    public static final String KIND_TEAM = "team";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_DISCARDED = "discarded";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "category", length = 32)
    private String category;

    @Column(name = "level", length = 32)
    private String level;

    @Column(name = "awarded_at")
    private LocalDate awardedAt;

    @Column(name = "college_name", length = 64)
    private String collegeName;

    @Column(name = "red_doc_url", length = 512)
    private String redDocUrl;

    @Column(name = "member_names", columnDefinition = "TEXT")
    private String memberNames;

    /** OCR 阶段填充的结构化结果；当前阶段为 null。 */
    @Column(name = "parsed_json", columnDefinition = "JSON")
    private String parsedJson;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}