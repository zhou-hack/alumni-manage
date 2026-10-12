package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 校友荣誉（统计接口唯一数据源）。created_at/updated_at 由数据库管理。
 * user 为只读关联，用于列表 keyword 筛选 join users → alumni_base。
 */
@Getter
@Setter
@Entity
@Table(name = "honors")
public class Honor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 可关联档案（非 Archive 的 category 时为 NULL） */
    @Column(name = "archive_id")
    private Long archiveId;

    /** [M03] 团队荣誉批次 UUID（同批成员共享；单人提交为 NULL） */
    @Column(name = "team_id", length = 36)
    private String teamId;

    /** [M03 扩展] 1=团队荣誉公示行（团队入库时挂提交人名下）；个人行=0 */
    @Column(name = "is_team_record", nullable = false)
    private Boolean isTeamRecord = false;

    /** [M03 扩展] leader/member（公示行固定 leader，个人行固定 member；单人荣誉为 NULL） */
    @Column(name = "team_role", length = 16)
    private String teamRole;

    /** [M03] 团队荣誉原始成员名单文本（人名解析依据，超长截断） */
    @Column(name = "member_names", length = 1000)
    private String memberNames;

    /** [M03] 提交人 users.id（单人提交=本人；团队/导入=操作者） */
    @Column(name = "created_by")
    private Long createdBy;

    /** [M03] 红头文件/官方文件链接（附件预览地址或外部 URL） */
    @Column(name = "red_doc_url", length = 512)
    private String redDocUrl;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    /** [P1/M13] 标题全拼（创建/修改时经 PinyinUtils 预生成） */
    @Column(name = "title_pinyin", length = 192)
    private String titlePinyin;

    /** [P1/M13] 标题拼音首字母（创建/修改时经 PinyinUtils 预生成） */
    @Column(name = "title_pinyin_abbr", length = 96)
    private String titlePinyinAbbr;

    /** scholarship/competition……对齐 honor_category 字典 */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** national/provincial/school…… */
    @Column(name = "level", nullable = false, length = 32)
    private String level;

    /** 纯日期 YYYY-MM-DD */
    @Column(name = "awarded_at", nullable = false)
    private LocalDate awardedAt;

    /** pending_counselor/pending_first/pending_second/approved/rejected（走 Reviews 通道） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** [P1/M17] 大屏上下架状态：online 上架 / offline 下架（与审核 status 解耦，默认 offline） */
    @Column(name = "screen_status", nullable = false, length = 16)
    private String screenStatus = "offline";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /** 只读关联：列表筛选 join 用，不由此维护外键 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
}
