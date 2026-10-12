package com.alumni.honors.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 附件（本模块维护引用关系）：上传后为 unreferenced，
 * 荣誉提交时回填 ref_id 转为 referenced；荣誉换绑时先释放旧附件再引用新附件。
 */
@Getter
@Setter
@Entity(name = "honorsAttachment")
@Table(name = "attachments")
public class Attachment {

    /** 状态常量：未引用 */
    public static final String STATUS_UNREFERENCED = "unreferenced";
    /** 状态常量：已引用 */
    public static final String STATUS_REFERENCED = "referenced";
    /** 本模块引用附件的业务类型 */
    public static final String BIZ_TYPE_HONOR = "honor";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 业务记录 ID，NULL=未引用 */
    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "referenced_at")
    private Instant referencedAt;
}
