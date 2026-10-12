package com.alumni.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校友管理平台 - 唯一启动入口（模块化单体）。
 *
 * 全部业务模块（com.alumni.{module}）装配进同一 JVM、同一端口（:8080）、同一部署单元 alumni-platform.jar。
 * 模块间协作全部为 JVM 内部调用（共享库直写 + Spring bean 注入），不存在任何跨模块 HTTP。
 *
 * 装配规则：
 * 1. ModuleBeanNameGenerator 为跨模块同名组件生成带模块前缀的 bean 名；
 * 2. 各模块自带的 *Application 启动类被排除（仅供模块独立调试时使用，避免二次组件扫描）；
 * 3. 安全栈统一由 common 提供（全平台唯一 SecurityFilterChain / JwtAuthFilter / AuthPrincipal / PasswordEncoder），
 *    模块不再携带各自的安全配置；auth 模块保留自己的 JwtService 负责 token 签发；
 * 4. JPA 仓储/实体按模块独立包名扫描，互不覆盖。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "com.alumni",
        nameGenerator = ModuleBeanNameGenerator.class,
        excludeFilters = {
                // 各模块独立运行用启动类（单体装配时排除，防止其自带 @ComponentScan 重复注册）
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.alumni\\.(auth|dicts|attachments|archives|reviews|stats|consents|honors|showcase|announcement|drafts|outstanding|directory|wall|counselors|teachers|tags|notifications|admin|audit|screen|photo|orgsync)\\.[A-Za-z0-9]+Application")
        })
@EnableJpaRepositories(basePackages = "com.alumni", nameGenerator = ModuleBeanNameGenerator.class)
@EntityScan("com.alumni")
@EnableScheduling
@EnableAsync
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
