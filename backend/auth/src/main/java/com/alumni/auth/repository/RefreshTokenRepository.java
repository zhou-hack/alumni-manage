package com.alumni.auth.repository;

import com.alumni.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 作废该用户全部已签发 refresh_token（登出 / 重置密码后强制重新登录） */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);
}
