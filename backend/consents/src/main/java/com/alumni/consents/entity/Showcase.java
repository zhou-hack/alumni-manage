package com.alumni.consents.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 上架记录（行存在=上架中，下架=删行）。
 * 本模块仅用于撤回 screen 授权时的级联删除。
 */
@Getter
@Setter
@Entity(name = "consentsShowcase")
@Table(name = "showcase")
public class Showcase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_id", nullable = false)
    private Long archiveId;
}
