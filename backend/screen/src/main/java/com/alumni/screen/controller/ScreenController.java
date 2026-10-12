package com.alumni.screen.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.screen.common.ApiResponse;
import com.alumni.screen.dto.ScreenDtos.ConfigItem;
import com.alumni.screen.dto.ScreenDtos.PageResult;
import com.alumni.screen.dto.ScreenDtos.StatsPayload;
import com.alumni.screen.dto.ScreenDtos.UpsertRequest;
import com.alumni.screen.service.HonorScreenConfigService;
import com.alumni.screen.service.HonorStatsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 荣誉大屏管理 Controller（路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET    /admin/screen/config/active —— 当前生效配置（school_admin/system_admin）</li>
 *   <li>GET    /admin/screen/config          —— 配置分页（status 筛选）</li>
 *   <li>GET    /admin/screen/config/{id}     —— 配置详情</li>
 *   <li>POST   /admin/screen/config          —— 新建配置</li>
 *   <li>PUT    /admin/screen/config/{id}     —— 更新配置</li>
 *   <li>DELETE /admin/screen/config/{id}     —— 删除非 active 配置</li>
 *   <li>GET    /admin/screen/stats           —— 统计分析聚合（4 维度）</li>
 * </ul>
 *
 * <p>权限：school_admin / system_admin（URL 级 SecurityConfig 已限，Service 内二次校验）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/screen")
public class ScreenController {

    private final HonorScreenConfigService configService;
    private final HonorStatsService statsService;

    public ScreenController(HonorScreenConfigService configService, HonorStatsService statsService) {
        this.configService = configService;
        this.statsService = statsService;
    }

    @GetMapping("/config/active")
    public ApiResponse<ConfigItem> getActive() {
        return ApiResponse.ok(configService.getActive());
    }

    @GetMapping("/config")
    public ApiResponse<PageResult<ConfigItem>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(configService.list(status, page, pageSize));
    }

    @GetMapping("/config/{id}")
    public ApiResponse<ConfigItem> detail(@PathVariable long id) {
        return ApiResponse.ok(configService.detail(id));
    }

    @PostMapping("/config")
    public ApiResponse<ConfigItem> create(@AuthenticationPrincipal AuthPrincipal operator,
                                          @Valid @RequestBody UpsertRequest request) {
        return ApiResponse.ok(configService.create(operator, request));
    }

    @PutMapping("/config/{id}")
    public ApiResponse<ConfigItem> update(@AuthenticationPrincipal AuthPrincipal operator,
                                          @PathVariable long id,
                                          @Valid @RequestBody UpsertRequest request) {
        return ApiResponse.ok(configService.update(operator, id, request));
    }

    @DeleteMapping("/config/{id}")
    public ApiResponse<java.util.Map<String, Object>> delete(@AuthenticationPrincipal AuthPrincipal operator,
                                                             @PathVariable long id) {
        configService.delete(operator, id);
        return ApiResponse.ok(java.util.Map.of("id", id, "deleted", true));
    }

    @GetMapping("/stats")
    public ApiResponse<StatsPayload> stats(
            @AuthenticationPrincipal AuthPrincipal operator,
            @RequestParam(name = "top_n", defaultValue = "10") int topN) {
        return ApiResponse.ok(statsService.stats(operator, topN));
    }
}
