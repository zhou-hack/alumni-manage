package com.alumni.stats.repository;

import com.alumni.stats.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 校友数 = COUNT(users)，直接使用 JpaRepository 自带 count()。
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
