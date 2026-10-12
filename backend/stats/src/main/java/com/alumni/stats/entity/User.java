package com.alumni.stats.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台账号（统计只读，仅映射 COUNT 所需的主键列）。
 */
@Getter
@Setter
@Entity(name = "statsUser")
@Table(name = "users")
public class User {

    @Id
    private Long id;
}
