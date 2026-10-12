package com.alumni.tags.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.tags.common.ApiResponse;
import com.alumni.tags.dto.TagDtos.BindRequest;
import com.alumni.tags.dto.TagDtos.CreateRequest;
import com.alumni.tags.dto.TagDtos.PageResult;
import com.alumni.tags.dto.TagDtos.TagItem;
import com.alumni.tags.dto.TagDtos.UpdateRequest;
import com.alumni.tags.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 标签控制器（Controller 层，P1 [M13]；路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET    /tags                          —— 标签列表（登录；?group=&keyword=&page=&page_size=）</li>
 *   <li>POST   /tags                          —— 创建标签（school_admin，同域重名 409 TAG_DUPLICATED，201）</li>
 *   <li>PATCH  /tags/{id}                     —— 改名 / 调排序（school_admin）</li>
 *   <li>DELETE /tags/{id}                     —— 删除（school_admin；被引用 409 RESOURCE_REFERENCED）</li>
 *   <li>PUT    /tags/bindings/{bizType}/{bizId} —— 全量覆盖式绑定（college_admin/school_admin）</li>
 *   <li>GET    /tags/bindings/{bizType}/{bizId} —— 查询已绑标签（登录）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。</p>
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** POST /api/v1/tags —— 创建标签，成功返回 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<TagItem>> create(@Valid @RequestBody CreateRequest request) {
        TagItem data = tagService.create(currentUser(), request.name(), request.group(), request.sort());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** GET /api/v1/tags —— 标签列表（?group=&keyword=&page=&page_size=） */
    @GetMapping
    public ApiResponse<PageResult> page(
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(tagService.page(currentUser(), group, keyword, page, pageSize));
    }

    /** PATCH /api/v1/tags/{id} —— 改名 / 调排序（字段均可选，给了才改） */
    @PatchMapping("/{id}")
    public ApiResponse<TagItem> update(@PathVariable long id,
                                       @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(tagService.update(currentUser(), id, request.name(), request.sort()));
    }

    /** DELETE /api/v1/tags/{id} —— 删除标签 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id) {
        tagService.delete(currentUser(), id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    /** PUT /api/v1/tags/bindings/{bizType}/{bizId} —— 全量覆盖式设置标签，返回最新已绑列表 */
    @PutMapping("/bindings/{bizType}/{bizId}")
    public ApiResponse<List<TagItem>> bind(@PathVariable String bizType, @PathVariable long bizId,
                                           @Valid @RequestBody BindRequest request) {
        return ApiResponse.ok(tagService.bind(currentUser(), bizType, bizId, request.tagIds()));
    }

    /** GET /api/v1/tags/bindings/{bizType}/{bizId} —— 查询已绑标签 */
    @GetMapping("/bindings/{bizType}/{bizId}")
    public ApiResponse<List<TagItem>> bindings(@PathVariable String bizType, @PathVariable long bizId) {
        return ApiResponse.ok(tagService.bindings(currentUser(), bizType, bizId));
    }

    /** 从 SecurityContext 取已认证主体（SecurityConfig 已保证 /api/v1/tags/** 必须登录） */
    private AuthPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
