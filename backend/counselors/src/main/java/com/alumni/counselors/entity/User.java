package com.alumni.counselors.entity;

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
 * 平台账号（本模块仅使用 role='counselor' 的行）。
 * 密码存 BCrypt(前端 SHA-256 散列值)，永不落明文；status 为 P1 停权列（active/suspended）。
 */
@Getter
@Setter
@Entity(name = "counselorsUser")
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外对接 UUID（v4 随机，创建时生成，不可变；内部关联仍用 id） */
    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "alumni_base_id", nullable = false, unique = true)
    private Long alumniBaseId;

    @Column(name = "student_id", nullable = false, unique = true, length = 32)
    private String studentId;

    @Column(name = "phone", nullable = false, unique = true, length = 16)
    private String phone;

    @Column(name = "nickname", nullable = false, length = 64)
    private String nickname;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** [P1/M15] active/suspended（停权：Token 即刻作废，登录 403） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    private Instant updatedAt;
}
