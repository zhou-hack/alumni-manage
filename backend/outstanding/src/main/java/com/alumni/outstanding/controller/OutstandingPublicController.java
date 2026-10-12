package com.alumni.outstanding.controller;

import com.alumni.outstanding.common.ApiResponse;
import com.alumni.outstanding.dto.OutstandingDtos.OutstandingListResult;
import com.alumni.outstanding.service.OutstandingAlumniService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优秀校友公开控制器（扩展模块 [M14]；路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET /alumni/outstanding?limit= —— 首页环形展厅数据（公开，免认证，仅 published）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。
 * 隐私红线：仅返回精选位编辑的展示数据，绝不出参手机号/身份证等字段。</p>
 */
@RestController
public class OutstandingPublicController {

    private final OutstandingAlumniService outstandingService;

    public OutstandingPublicController(OutstandingAlumniService outstandingService) {
        this.outstandingService = outstandingService;
    }

    /** GET /api/v1/alumni/outstanding?limit=16 —— 公开列表（默认全部 published） */
    @GetMapping("/api/v1/alumni/outstanding")
    public ApiResponse<OutstandingListResult> outstanding(
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ApiResponse.ok(outstandingService.outstanding(limit));
    }
}
