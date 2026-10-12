package com.alumni.common.security;

import com.alumni.common.ApiResponse;
import com.alumni.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全平台统一鉴权规则（模块化单体唯一 SecurityFilterChain；合并原各模块 SecurityConfig）。
 * 规则按「先具体后通配」声明；角色细粒度与本人归属校验仍在各模块 Service 层。
 *
 * 汇总（API 规范 v1.2）：
 * - /auth/** 公开（logout、me 需登录）
 * - /screen/feed 公开；/showcase/** 仅 school_admin
 * - /imports/** 院级/校级/系统管理员；/stats/** 审核角色（含导员）
 * - attachments：POST 登录 / DELETE 审核角色 / preview 放行（业务层按归属继承）
 * - archives：POST 校友端 / GET 列表审核角色（含导员）/ 其余登录
 * - dicts：GET 登录 / 写 school_admin
 * - reviews / consents / honors：登录
 * - drafts：登录（草稿本人可见/可删，业务层校验归属）
 * - announcements：GET 登录（非管理员仅见 published）；写 college_admin/school_admin
 * - alumni（outstanding/directory [M14]/[M15]）：GET 公开免认证（红线同 /screen/feed）
 * - /admin/outstanding-alumni/**：仅 school_admin（优秀校友精选位维护）
 * - wall [M16]：GET /wall/messages 公开；mine/POST/DELETE 登录；/admin/wall/** school_admin
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // auth：logout / me 需登录，其余公开
                        .requestMatchers("/api/v1/auth/logout", "/api/v1/auth/me").authenticated()
                        // showcase / screen
                        .requestMatchers(HttpMethod.GET, "/api/v1/screen/**").permitAll()
                        .requestMatchers("/api/v1/showcase/**").hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // imports / stats（导员可看统计看板；批量导入：院级/校级/系统）
                        .requestMatchers("/api/v1/imports/**")
                            .hasAnyRole("COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // stats 公开读：H5 数据看板（/m/dashboard）面向全校师生，需要免登录可读
                        .requestMatchers(HttpMethod.GET, "/api/v1/stats/overview",
                                "/api/v1/stats/colleges", "/api/v1/stats/honors/distribution").permitAll()
                        // 荣誉大屏聚合（portal /dashboard）：公开免登录可读，门户访客直接看
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard-honor").permitAll()
                        .requestMatchers("/api/v1/stats/**")
                            .hasAnyRole("COUNSELOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // attachments
                        .requestMatchers(HttpMethod.POST, "/api/v1/attachments").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/attachments/**")
                            .hasAnyRole("COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // attachments：preview 放行（与上文注释一致，业务层按归属继承判定
                        // PUBLIC 免认证 / NEED_LOGIN 401 / DENY 403），否则公开图片匿名不可见、nginx 共享缓存无意义
                        .requestMatchers(HttpMethod.GET, "/api/v1/attachments/*/preview").permitAll()
                        // archives
                        .requestMatchers(HttpMethod.POST, "/api/v1/archives").hasAnyRole("STUDENT", "ALUMNI")
                        .requestMatchers(HttpMethod.GET, "/api/v1/archives")
                            .hasAnyRole("COUNSELOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers("/api/v1/archives/**").authenticated()
                        // dicts：GET 登录，写 school_admin / system_admin
                        .requestMatchers(HttpMethod.GET, "/api/v1/dicts/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/dicts/**")
                            .hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/dicts/**")
                            .hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/dicts/**")
                            .hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // [M03] 团队荣誉提交：导员/院系/校级/系统管理员（须先于通用 /honors/** 规则；
                        // GET /honors/team/{id} 走通用登录规则，提交者/审核角色校验在 Service 层）
                        .requestMatchers(HttpMethod.POST, "/api/v1/honors/team")
                            .hasAnyRole("COUNSELOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // [M03] 荣誉管理端（分级列表/导入/导出）：院系/校级/系统管理员（导员不可导入，学院隔离在 Service）
                        .requestMatchers("/api/v1/admin/honors/**")
                            .hasAnyRole("COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // reviews / consents / honors / drafts：登录
                        .requestMatchers("/api/v1/reviews/**", "/api/v1/consents/**", "/api/v1/honors/**",
                                "/api/v1/drafts/**")
                            .authenticated()
                        // announcements：GET 公开（免登录可读，非管理员仅见 published，业务层过滤）；写管理员
                        .requestMatchers(HttpMethod.GET, "/api/v1/announcements/**").permitAll()
                        .requestMatchers("/api/v1/announcements/**")
                            .hasAnyRole("COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // alumni 公开检索（outstanding/directory [M14]/[M15]）：免认证，
                        // 红线同 /screen/feed —— 仅公开 approved+上架+已授权数据（SQL 口径保证）
                        .requestMatchers(HttpMethod.GET, "/api/v1/alumni/outstanding").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/alumni/directory/**").permitAll()
                        // [V18] 校友大屏展示字段：GET 公开读（供 portal 大屏 / 校友通讯录），
                        // 写路径见下方 alumni_profile 路由
                        .requestMatchers(HttpMethod.GET, "/api/v1/alumni/*/profile").permitAll()
                        // [V18] 校友本人编辑自己档案：登录即可，业务层校验 alumni_base_id == operator.alumniBaseId
                        .requestMatchers("/api/v1/me/**").authenticated()
                        // [V18] 校级/系统管理员编辑任意校友档案
                        .requestMatchers("/api/v1/admin/alumni/**")
                            .hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // 优秀校友精选位管理：school_admin / system_admin
                        .requestMatchers("/api/v1/admin/outstanding-alumni/**")
                            .hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // 人员管理 [M15]：停权/恢复/交接/留痕仅系统管理员（须先于通用 /admin/users/** 规则）
                        .requestMatchers("/api/v1/admin/users/*/suspend", "/api/v1/admin/users/*/resume",
                                "/api/v1/admin/users/*/handover", "/api/v1/admin/users/*/change-logs")
                            .hasRole("SYSTEM_ADMIN")
                        // 批量重置密码（重置为默认密码）：仅系统管理员（须先于通用 /admin/users/** 规则）
                        .requestMatchers("/api/v1/admin/users/batch-reset-password").hasRole("SYSTEM_ADMIN")
                        // 人员管理·列表查询与单个新增：导员/院级/校级/系统（数据范围隔离在 Service：
                        // 院级锁本院、导员锁 counselor_scopes；编辑/重置密码等写操作仍仅校级/系统）
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users")
                            .hasAnyRole("COUNSELOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users")
                            .hasAnyRole("COUNSELOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // 人员管理（其余写操作）：校级 / 系统管理员
                        .requestMatchers("/api/v1/admin/users/**").hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // wall 留言墙 [M16]：公开列表免认证（仅 approved）；mine/写/撤回登录（归属校验在 Service）；
                        // 管理端 院系/校级/系统管理员均可审核（与档案/荣誉审核保持一致）
                        .requestMatchers(HttpMethod.GET, "/api/v1/wall/messages").permitAll()
                        .requestMatchers("/api/v1/wall/messages/**").authenticated()
                        .requestMatchers("/api/v1/admin/wall/**")
                            .hasAnyRole("COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // screen 荣誉大屏管理 [M03 扩展]：仅 school_admin / system_admin
                        .requestMatchers("/api/v1/admin/screen/**").hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // photo 照片管理：用户提交（登录） / 后台审核 / 推送（review/push role 二次校验）
                        .requestMatchers(HttpMethod.POST, "/api/v1/photos/**").authenticated()
                        .requestMatchers("/api/v1/admin/photos/**").hasAnyRole("COUNSELOR", "COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // org-sync 组织架构同步：仅校级 / 系统管理员
                        .requestMatchers("/api/v1/admin/org-sync/**").hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // hero 首页轮播：公开列表免认证（仅 published）；维护仅校级 / 系统管理员
                        .requestMatchers(HttpMethod.GET, "/api/v1/hero/slides").permitAll()
                        .requestMatchers("/api/v1/admin/hero/**").hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // teachers 教师库：手动同步 / 同步记录仅系统管理员（须先于通用列表规则）；列表校级 / 系统管理员
                        .requestMatchers("/api/v1/admin/teachers/sync", "/api/v1/admin/teachers/sync-logs")
                            .hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/api/v1/admin/teachers/**")
                            .hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // ============ P1 新增（《[定稿]API规范P1》）============
                        // counselors [M34]：导员账号管理，school_admin / system_admin
                        .requestMatchers("/api/v1/counselors/**").hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // tags [M13]：GET 登录；写 school_admin / system_admin；bindings GET 登录 / PUT 院系或校级管理员
                        .requestMatchers(HttpMethod.GET, "/api/v1/tags/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/tags/bindings/**")
                            .hasAnyRole("COLLEGE_ADMIN", "SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers("/api/v1/tags/**").hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        // search [M13]：登录
                        .requestMatchers("/api/v1/search/**").authenticated()
                        // notifications / notification-subscriptions [M14]：登录（归属校验在 Service）
                        .requestMatchers("/api/v1/notifications/**", "/api/v1/notification-subscriptions/**")
                            .authenticated()
                        // field-permissions [M15]：GET 校级/系统；PUT 仅系统管理员
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/field-permissions/**")
                            .hasAnyRole("SCHOOL_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/field-permissions/**").hasRole("SYSTEM_ADMIN")
                        // audit-logs [M16]：仅系统管理员（不设独立审计角色，P1-Q4）
                        .requestMatchers("/api/v1/audit-logs/**").hasRole("SYSTEM_ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未登录或 Token 已过期"))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, objectMapper,
                                HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "无权限访问")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt（密码链路：前端 SHA-256 → 服务端 BCrypt 入库），全平台唯一提供方 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeError(HttpServletResponse response, ObjectMapper objectMapper,
                            int status, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode, message)));
    }

    /** 开发联调用 CORS：放行本机各端口测试页跨域调用（生产收敛到具体域名） */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
