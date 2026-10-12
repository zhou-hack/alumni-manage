package com.alumni.showcase.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台账号（本模块仅读取 feed 出参所需列）。
 */
@Getter
@Setter
@Entity(name = "showcaseUser")
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname", nullable = false, length = 64)
    private String nickname;

    @Column(name = "alumni_base_id", nullable = false)
    private Long alumniBaseId;
}
