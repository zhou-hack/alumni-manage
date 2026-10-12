package com.alumni.honors.controller;

import com.alumni.honors.common.ApiResponse;
import com.alumni.honors.common.BizException;
import com.alumni.honors.common.ErrorCode;
import com.alumni.honors.dto.HonorDtos.CreateHonorRequest;
import com.alumni.honors.dto.HonorDtos.UpdateHonorRequest;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.service.HonorService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校友荣誉控制器（Controller 层）。
 *
 * <p>实现《[定稿]API规范》第五章接口（路径相对于 /api/v1）：</p>
 * <ul>
 *   <li>POST  /honors      —— 创建荣誉（student / alumni，生成 Reviews 待审记录）</li>
 *   <li>GET   /honors      —— 荣誉列表（任意登录用户，分页 + 多条件筛选）</li>
 *   <li>GET   /honors/{id} —— 荣誉详情（任意登录用户）</li>
 *   <li>PATCH /honors/{id} —— 修改荣誉（仅本人，且未进入审核）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应。
 * 不含任何业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/honors")
public class HonorController {

    private final HonorService honorService;

    @PersistenceContext
    private EntityManager em;

    public HonorController(HonorService honorService) {
        this.honorService = honorService;
    }

    /** POST /api/v1/honors —— 创建荣誉，成功返回 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreateHonorRequest request) {
        Map<String, Object> data = honorService.create(currentUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** GET /api/v1/honors —— 荣誉列表（分页 + keyword/college_id/grad_year/category/status/日期区间筛选） */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "college_id", required = false) Long collegeId,
            @RequestParam(name = "grad_year", required = false) Integer gradYear,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(honorService.list(keyword, collegeId, gradYear, category, status,
                dateFrom, dateTo, sort, page, pageSize));
    }

    /** GET /api/v1/honors/{id} —— 荣誉详情（含 attachment_ids） */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        return ApiResponse.ok(honorService.detail(id));
    }

    /** PATCH /api/v1/honors/{id} —— 修改荣誉（仅本人，且 status=pending_first） */
    @PatchMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id,
                                                   @Valid @RequestBody UpdateHonorRequest request) {
        return ApiResponse.ok(honorService.update(currentUser(), id, request));
    }

    /**
     * GET /api/v1/honors/{id}/alumni-base-id —— 校友风采跳转用：返回荣誉所属用户的 alumni_base_id。
     * 用 users ⋈ alumni_base 反查，避免前端再发 /admin/users/{userId} 多一跳。
     */
    @GetMapping("/{id}/alumni-base-id")
    public ApiResponse<Map<String, Object>> alumniBaseId(@PathVariable long id) {
        // 用 getResultList() 一次性取回，避免 getResultStream() 流被 Hibernate 提前关闭导致 500
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                "SELECT u.alumni_base_id FROM honors h " +
                "JOIN users u ON u.id = h.user_id WHERE h.id = ?1"
        ).setParameter(1, id).getResultList();
        Object first = rows.isEmpty() ? null : rows.get(0);
        Number abId = first == null ? null : (Number) first;
        if (abId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "荣誉不存在或用户未绑定校友库");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alumni_base_id", abId.longValue());
        return ApiResponse.ok(data);
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/honors/** 必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
