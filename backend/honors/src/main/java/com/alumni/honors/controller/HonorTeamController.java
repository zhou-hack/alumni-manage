package com.alumni.honors.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.common.ApiResponse;
import com.alumni.honors.dto.HonorTeamDtos.TeamCreateRequest;
import com.alumni.honors.dto.HonorTeamDtos.TeamCreateResult;
import com.alumni.honors.dto.HonorTeamDtos.TeamDetailResult;
import com.alumni.honors.service.HonorTeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [M03] 团队荣誉控制器（路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>POST /honors/team        —— 导员/管理员提交团队荣誉（人名自动识别关联，201）</li>
 *   <li>GET  /honors/team/{id}   —— 团队详情（提交者或院系/校级/系统管理员）</li>
 * </ul>
 *
 * <p>URL 级角色限制见 common SecurityConfig；角色细粒度与归属校验在 Service 层。</p>
 */
@RestController
@RequestMapping("/api/v1/honors/team")
public class HonorTeamController {

    private final HonorTeamService honorTeamService;

    public HonorTeamController(HonorTeamService honorTeamService) {
        this.honorTeamService = honorTeamService;
    }

    /** POST /api/v1/honors/team —— 团队荣誉提交（人名识别四桶结果），成功返回 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<TeamCreateResult>> create(
            @AuthenticationPrincipal AuthPrincipal operator,
            @Valid @RequestBody TeamCreateRequest request) {
        TeamCreateResult data = honorTeamService.create(operator, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** GET /api/v1/honors/team/{team_id} —— 团队详情（成员留证 + 同批荣誉摘要） */
    @GetMapping("/{teamId}")
    public ApiResponse<TeamDetailResult> detail(@AuthenticationPrincipal AuthPrincipal operator,
                                                @PathVariable String teamId) {
        return ApiResponse.ok(honorTeamService.detail(operator, teamId));
    }
}
