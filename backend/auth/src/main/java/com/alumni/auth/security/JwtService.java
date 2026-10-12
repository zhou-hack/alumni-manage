package com.alumni.auth.security;

import com.alumni.common.security.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * access_token 签发与校验（HS256，2 小时有效）。
 *
 * <p>V18：签发时同时写入 alumni_base_id claim，方便 alumni 端「编辑自己档案」
 * 在不查库的情况下拿到 owner ID。</p>
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long accessExpireSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-expire-seconds}") long accessExpireSeconds) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpireSeconds = accessExpireSeconds;
    }

    public long getAccessExpireSeconds() {
        return accessExpireSeconds;
    }

    /**
     * @param userId      users.id
     * @param studentId   users.student_id
     * @param role        角色
     * @param alumniBaseId users.alumni_base_id（校友本人端点鉴权用；非校友端可传 null）
     */
    public String generate(Long userId, String studentId, String role, Long alumniBaseId) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("student_id", studentId)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessExpireSeconds * 1000));
        if (alumniBaseId != null) {
            builder.claim("alumni_base_id", alumniBaseId);
        }
        return builder.signWith(key).compact();
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Number abIdNum = claims.get("alumni_base_id", Number.class);
            Long alumniBaseId = abIdNum == null ? null : abIdNum.longValue();
            return Optional.of(new AuthPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("student_id", String.class),
                    claims.get("role", String.class),
                    alumniBaseId));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
