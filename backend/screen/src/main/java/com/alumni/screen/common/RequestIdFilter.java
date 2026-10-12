package com.alumni.screen.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求 ID 过滤器：每个请求分配 request_id（优先取 X-Request-Id header，便于跨服务追踪）
 * 写入响应头与 RequestIdHolder，由全局异常处理器统一透出到响应体。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rid = request.getHeader(HEADER);
        if (rid == null || rid.isBlank()) {
            rid = UUID.randomUUID().toString().replace("-", "");
        }
        RequestIdHolder.set(rid);
        response.setHeader(HEADER, rid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestIdHolder.clear();
        }
    }
}
