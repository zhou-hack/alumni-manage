package com.alumni.orgsync.entity;

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
@Table(name = "org_sync_log")
public class OrgSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "sync_type", nullable = false, length = 32)
    private String syncType;

    @Column(nullable = false, length = 16)
    private String mode = "incremental";

    @Column(nullable = false)
    private Integer total = 0;

    @Column(nullable = false)
    private Integer inserted = 0;

    @Column(nullable = false)
    private Integer updated = 0;

    @Column(nullable = false)
    private Integer skipped = 0;

    @Column(nullable = false)
    private Integer failed = 0;

    @Column(columnDefinition = "json")
    private String failures;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, length = 16)
    private String status = "running";

    @Column(length = 255)
    private String remark;
}
