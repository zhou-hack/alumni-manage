package com.alumni.showcase.controller;

import com.alumni.showcase.common.ApiResponse;
import com.alumni.showcase.common.BizException;
import com.alumni.showcase.common.ErrorCode;
import com.alumni.showcase.repository.FeedProjection;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.showcase.service.ShowcaseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 展览馆 / 大屏控制器（Controller 层）。
 *
 * <p>实现《[定稿]API规范》第八章接口（路径相对于 /api/v1）：</p>
 * <ul>
 *   <li>POST /showcase/{archive_id}/publish —— 上架（school_admin）</li>
 *   <li>POST /showcase/{archive_id}/unpublish —— 下架（school_admin）</li>
 *   <li>GET  /screen/feed?college_id= —— 大屏轮播数据（公开，免认证）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应。
 * 不含任何业务规则与 SQL。</p>
 */
@RestController
public class ShowcaseController {

    private final ShowcaseService showcaseService;

    public ShowcaseController(ShowcaseService showcaseService) {
        this.showcaseService = showcaseService;
    }

    /** POST /api/v1/showcase/{archive_id}/publish —— 上架 */
    @PostMapping("/api/v1/showcase/{archiveId}/publish")
    public ApiResponse<Map<String, Object>> publish(@AuthenticationPrincipal AuthPrincipal operator,
                                                    @PathVariable long archiveId) {
        requirePositiveId(archiveId, "archive_id");
        return ApiResponse.ok(showcaseService.publish(operator, archiveId));
    }

    /** POST /api/v1/showcase/{archive_id}/unpublish —— 下架 */
    @PostMapping("/api/v1/showcase/{archiveId}/unpublish")
    public ApiResponse<Map<String, Object>> unpublish(@AuthenticationPrincipal AuthPrincipal operator,
                                                      @PathVariable long archiveId) {
        requirePositiveId(archiveId, "archive_id");
        return ApiResponse.ok(showcaseService.unpublish(operator, archiveId));
    }

    /** GET /api/v1/screen/feed?college_id= —— 大屏轮播数据（公开） */
    @GetMapping("/api/v1/screen/feed")
    public ApiResponse<Map<String, Object>> feed(
            @RequestParam(name = "college_id", required = false) String collegeIdRaw) {
        List<FeedProjection> items = showcaseService.feed(parseCollegeId(collegeIdRaw));
        List<Map<String, Object>> list = new ArrayList<>(items.size());
        for (FeedProjection item : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", item.getId());
            map.put("title", item.getTitle());
            map.put("category", item.getCategory());
            map.put("owner", item.getOwner());
            map.put("college", item.getCollege());
            map.put("grad_year", item.getGradYear());
            map.put("cover_url", item.getCoverUrl());
            list.add(map);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    /** 路径变量必须为正整数 */
    private void requirePositiveId(long id, String name) {
        if (id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, name + " 必须为正整数");
        }
    }

    /** 解析可选 query 参数 college_id，未传返回 null，非法抛出 INVALID_PARAMETER */
    private Long parseCollegeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "college_id 必须为正整数");
        }
    }
}
