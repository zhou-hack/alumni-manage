package com.alumni.counselors.repository;

import com.alumni.counselors.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 手机号全平台唯一（uk_users_phone），用于创建/换绑前查重 */
    Optional<User> findByPhone(String phone);
}
