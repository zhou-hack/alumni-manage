package com.alumni.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * access_token 验签（HS256，与 auth 模块同一密钥；本类只验不签，签发见 auth 模块 AuthTokenService）。
 */
@Component
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            // alumni_base_id 可空（顾问/管理端未必绑定 alumni_base）
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
