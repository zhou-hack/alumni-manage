package com.alumni.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Authorization: Bearer <access_token>，有效则写入 SecurityContext。
 * 公开路径（attachments preview / screen feed 等）未携带 token 时放行，由 SecurityConfig 与业务层判定。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SuspendedUserStore suspendedUserStore;

    public JwtAuthFilter(JwtService jwtService, SuspendedUserStore suspendedUserStore) {
        this.jwtService = jwtService;
        this.suspendedUserStore = suspendedUserStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)) {
            jwtService.parse(header.substring(PREFIX.length()))
                    // P1 [M15]：停权账号已签发 Token 即刻作废（Redis 停权标记命中即视为未认证）
                    .filter(principal -> !suspendedUserStore.isSuspended(principal.userId()))
                    .ifPresent(principal -> {
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().toUpperCase()));
                        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        filterChain.doFilter(request, response);
    }
}
