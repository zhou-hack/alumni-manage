package com.alumni.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * [M15] 角色—字段可见性矩阵（field_permissions）：(role, field) 唯一。
 * visibility：visible（明文）/ masked（脱敏）/ hidden（不出参）。
 * updated_at 由数据库 ON UPDATE 维护。
 */
@Getter
@Setter
@Entity
@Table(name = "field_permissions")
public class FieldPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** student/alumni/counselor/college_admin/school_admin/system_admin */
    @Column(name = "role", nullable = false, length = 32)
    private String role;

    /** phone/id_card/email/home_address/work_place/emergency_contact（白名单） */
    @Column(name = "field", nullable = false, length = 32)
    private String field;

    /** visible/masked/hidden */
    @Column(name = "visibility", nullable = false, length = 8)
    private String visibility;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
