package com.alumni.showcase.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.showcase.common.ApiResponse;
import com.alumni.showcase.common.BizException;
import com.alumni.showcase.common.ErrorCode;
import com.alumni.showcase.dto.HeroDtos.CreateRequest;
import com.alumni.showcase.dto.HeroDtos.Item;
import com.alumni.showcase.dto.HeroDtos.UpdateRequest;
import com.alumni.showcase.service.HeroSlideService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页轮播控制器（Controller 层，路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET  /hero/slides —— 公开列表（免认证，仅 published；门户首屏轮播数据源）</li>
 *   <li>GET  /admin/hero/slides —— 管理列表（全状态；school_admin / system_admin）</li>
 *   <li>POST /admin/hero/slides —— 新增</li>
 *   <li>PATCH /admin/hero/slides/{id} —— 修改（字段可选）</li>
 *   <li>DELETE /admin/hero/slides/{id} —— 删除</li>
 * </ul>
 *
 * <p>URL 级角色限制见 SecurityConfig（/admin/hero/** → SCHOOL_ADMIN / SYSTEM_ADMIN）；
 * 角色二次校验在 Service 层。不含业务规则与 SQL。</p>
 */
@RestController
public class HeroSlideController {

    private final HeroSlideService heroSlideService;

    public HeroSlideController(HeroSlideService heroSlideService) {
        this.heroSlideService = heroSlideService;
    }

    /** GET /api/v1/hero/slides —— 公开列表（免认证）；placement=hero（默认）/campus */
    @GetMapping("/api/v1/hero/slides")
    public ApiResponse<Map<String, Object>> publicList(
            @RequestParam(name = "placement", defaultValue = "hero") String placement) {
        List<Item> list = heroSlideService.publicList(placement);
        return ApiResponse.ok(Map.of("list", list, "total", list.size()));
    }

    /** GET /api/v1/admin/hero/slides —— 管理列表（全状态） */
    @GetMapping("/api/v1/admin/hero/slides")
    public ApiResponse<Map<String, Object>> adminList(@AuthenticationPrincipal AuthPrincipal operator) {
        List<Item> list = heroSlideService.adminList(operator);
        return ApiResponse.ok(Map.of("list", list, "total", list.size()));
    }

    /** POST /api/v1/admin/hero/slides —— 新增轮播位 */
    @PostMapping("/api/v1/admin/hero/slides")
    public ApiResponse<Item> create(@AuthenticationPrincipal AuthPrincipal operator,
                                    @Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(heroSlideService.create(operator, req));
    }

    /** PATCH /api/v1/admin/hero/slides/{id} —— 修改轮播位 */
    @PatchMapping("/api/v1/admin/hero/slides/{id}")
    public ApiResponse<Item> update(@AuthenticationPrincipal AuthPrincipal operator,
                                    @PathVariable long id,
                                    @Valid @RequestBody UpdateRequest req) {
        requirePositiveId(id);
        return ApiResponse.ok(heroSlideService.update(operator, id, req));
    }

    /** DELETE /api/v1/admin/hero/slides/{id} —— 删除轮播位 */
    @DeleteMapping("/api/v1/admin/hero/slides/{id}")
    public ApiResponse<Map<String, Object>> delete(@AuthenticationPrincipal AuthPrincipal operator,
                                                   @PathVariable long id) {
        requirePositiveId(id);
        heroSlideService.delete(operator, id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    /** 路径变量必须为正整数 */
    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "id 必须为正整数");
        }
    }
}
