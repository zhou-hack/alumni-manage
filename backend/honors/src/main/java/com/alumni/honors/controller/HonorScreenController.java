package com.alumni.honors.controller;

import com.alumni.honors.common.ApiResponse;
import com.alumni.honors.dto.HonorScreenDtos;
import com.alumni.honors.service.HonorScreenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校友荣誉大屏聚合接口（路径 /api/v1/dashboard-honor，公开免认证）。
 *
 * <p>替代原 /api/v1/stats/overview 等分散接口；面向 portal 端 /dashboard
 * 「校友荣誉大屏」页面，单次拉取大屏所需的全部数据（KPI + 4 张图表 +
 * 荣誉榜单 + 杰出校友）。</p>
 *
 * <p>权限：SecurityConfig 已将 GET /api/v1/dashboard-honor 配置为 permitAll，
 * 与 /api/v1/screen/** 公开档同口径，避免外部大屏跳板受 Token 失效影响。</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard-honor")
public class HonorScreenController {

    private final HonorScreenService service;

    public HonorScreenController(HonorScreenService service) {
        this.service = service;
    }

    /** GET /api/v1/dashboard-honor —— 荣誉大屏聚合数据 */
    @GetMapping
    public ApiResponse<HonorScreenDtos> dashboard() {
        return ApiResponse.ok(service.load());
    }
}
