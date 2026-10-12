package com.alumni.tags.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.tags.common.ApiResponse;
import com.alumni.tags.dto.SearchDtos.SearchResult;
import com.alumni.tags.dto.SearchDtos.SuggestResult;
import com.alumni.tags.service.SearchService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 增强检索控制器（Controller 层，P1 [M13]；路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET /search         —— 聚合检索（登录；拼音容错 / 数据范围红线 / 内存合并分页）</li>
 *   <li>GET /search/suggest —— 关键词联想（登录；limit 默认 10 最大 20）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** GET /api/v1/search —— 聚合检索（types/tag_ids 为逗号分隔；include_all 仅审核/管理角色生效） */
    @GetMapping
    public ApiResponse<SearchResult> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "types", required = false) String types,
            @RequestParam(name = "college_id", required = false) Long collegeId,
            @RequestParam(name = "grad_year", required = false) Integer gradYear,
            @RequestParam(name = "tag_ids", required = false) String tagIds,
            @RequestParam(name = "include_all", defaultValue = "false") boolean includeAll,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(searchService.search(currentUser(), keyword, types, collegeId,
                gradYear, tagIds, includeAll, page, pageSize));
    }

    /** GET /api/v1/search/suggest —— 关键词联想（?keyword=&limit=） */
    @GetMapping("/suggest")
    public ApiResponse<SuggestResult> suggest(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ApiResponse.ok(searchService.suggest(currentUser(), keyword, limit));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/search/** 必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
