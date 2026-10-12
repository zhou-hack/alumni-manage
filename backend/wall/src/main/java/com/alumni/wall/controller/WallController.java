package com.alumni.wall.controller;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.wall.common.ApiResponse;
import com.alumni.wall.dto.WallDtos.CreateRequest;
import com.alumni.wall.dto.WallDtos.MyItem;
import com.alumni.wall.dto.WallDtos.PageResult;
import com.alumni.wall.dto.WallDtos.PublicItem;
import com.alumni.wall.dto.WallDtos.UpdateRequest;
import com.alumni.wall.service.WallService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.List;
import java.util.Map;

/**
 * 留言墙控制器（扩展模块 [M16]；路径相对于 /api/v1）：
 *
 * <ul>
 *   <li>GET    /wall/messages                —— 公开列表（免登录，仅 approved，置顶优先时间倒序）</li>
 *   <li>GET    /wall/messages/mine           —— 我的留言（登录，含 pending/rejected/hidden + 软删记录）</li>
 *   <li>POST   /wall/messages                —— 提交留言（登录，一律 pending 待审核；限流 60s/1 条、日/5 条）</li>
 *   <li>PUT    /wall/messages/{id}           —— 编辑自己的 pending/rejected 留言（重置为 pending 走审核）</li>
 *   <li>POST   /wall/messages/{id}/resubmit  —— 重新发送已撤回的留言（基于原内容建一条新的 pending）</li>
 *   <li>DELETE /wall/messages/{id}           —— 撤回自己的留言（软删除：deleted_at = now，保留原行供重发）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。
 * 姓名/届次/学院不传，服务端从登录身份关联带出。</p>
 */
@RestController
@RequestMapping("/api/v1/wall/messages")
public class WallController {

    private final WallService wallService;

    public WallController(WallService wallService) {
        this.wallService = wallService;
    }

    /** GET /api/v1/wall/messages —— 公开列表（免登录；page_size 默认 50 上限 100） */
    @GetMapping
    public ApiResponse<PageResult<PublicItem>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "50") int pageSize) {
        return ApiResponse.ok(wallService.listPublic(page, pageSize));
    }

    /** GET /api/v1/wall/messages/mine —— 我的留言（全部状态，含软删记录；响应同公开列表的 {total,list} 包装） */
    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> mine(@AuthenticationPrincipal AuthPrincipal operator) {
        List<MyItem> list = wallService.mine(operator);
        return ApiResponse.ok(Map.of("total", list.size(), "list", list));
    }

    /** POST /api/v1/wall/messages —— 提交留言（新留言一律 pending），成功返回 201 */
    @PostMapping
    public ResponseEntity<ApiResponse<MyItem>> create(@AuthenticationPrincipal AuthPrincipal operator,
                                                      @Valid @RequestBody CreateRequest request,
                                                      HttpServletRequest httpRequest) {
        MyItem data = wallService.create(operator, request.content(), clientIp(httpRequest), request.showName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** PUT /api/v1/wall/messages/{id} —— 编辑自己的 pending/rejected 留言；成功后重置为 pending */
    @PutMapping("/{id}")
    public ApiResponse<MyItem> update(@AuthenticationPrincipal AuthPrincipal operator,
                                      @PathVariable long id,
                                      @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(wallService.update(operator, id, request.content(), request.showName()));
    }

    /** POST /api/v1/wall/messages/{id}/resubmit —— 基于已软删留言的 content 重新发送为 pending */
    @PostMapping("/{id}/resubmit")
    public ResponseEntity<ApiResponse<MyItem>> resubmit(@AuthenticationPrincipal AuthPrincipal operator,
                                                        @PathVariable long id,
                                                        HttpServletRequest httpRequest) {
        MyItem data = wallService.resubmit(operator, id, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    /** DELETE /api/v1/wall/messages/{id} —— 撤回自己的留言（软删除） */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> remove(@AuthenticationPrincipal AuthPrincipal operator,
                                                   @PathVariable long id) {
        wallService.remove(operator, id);
        return ApiResponse.ok(Map.of("id", id, "deleted", true));
    }

    /** 提交 IP 提取（X-Forwarded-For 优先，风控用，不返回前端） */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
