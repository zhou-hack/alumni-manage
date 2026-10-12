package com.alumni.photo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "photo_push_log")
public class PhotoPushLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "photo_id", nullable = false)
    private Long photoId;

    /** all_users / college / role / single_user */
    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(nullable = false)
    private Integer recipients;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
