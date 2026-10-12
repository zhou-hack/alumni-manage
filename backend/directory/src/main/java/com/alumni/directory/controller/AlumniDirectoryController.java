package com.alumni.directory.controller;

import com.alumni.directory.common.ApiResponse;
import com.alumni.directory.common.BizException;
import com.alumni.directory.common.ErrorCode;
import com.alumni.directory.dto.DirectoryDtos.DirectoryDetail;
import com.alumni.directory.dto.DirectoryDtos.PageResult;
import com.alumni.directory.service.AlumniDirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校友档案索引控制器（扩展模块 [M15]；路径相对于 /api/v1，公开免认证）：
 *
 * <ul>
 *   <li>GET /alumni/directory                 —— 公开名录检索（keyword/name/grad_year/college_id/student_id 组合 + 分页）</li>
 *   <li>GET /alumni/directory/{student_id}    —— 校友公开档案详情（优秀校友弹窗共用）</li>
 * </ul>
 *
 * <p>职责：参数解析 → 调用 Service → 组装统一响应；不含业务规则与 SQL。
 * 隐私红线：仅返回 approved+已上架+已授权的公开数据，绝不出参手机号/身份证等字段。</p>
 */
@RestController
public class AlumniDirectoryController {

    private final AlumniDirectoryService directoryService;

    public AlumniDirectoryController(AlumniDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    /** GET /api/v1/alumni/directory —— 公开名录检索（全部参数可选） */
    @GetMapping("/api/v1/alumni/directory")
    public ApiResponse<PageResult> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "grad_year", required = false) String gradYearRaw,
            @RequestParam(name = "college_id", required = false) String collegeIdRaw,
            @RequestParam(name = "student_id", required = false) String studentId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(directoryService.search(keyword, name,
                parseGradYear(gradYearRaw), parseCollegeId(collegeIdRaw), studentId, page, pageSize));
    }

    /** GET /api/v1/alumni/directory/{student_id} —— 公开档案详情 */
    @GetMapping("/api/v1/alumni/directory/{studentId}")
    public ApiResponse<DirectoryDetail> detail(@PathVariable String studentId) {
        return ApiResponse.ok(directoryService.detail(studentId));
    }

    /** 解析可选 query 参数 grad_year：未传返回 null，非法抛 INVALID_PARAMETER */
    private Integer parseGradYear(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "grad_year 必须为整数年份");
        }
    }

    /** 解析可选 query 参数 college_id：未传返回 null，非法抛 INVALID_PARAMETER */
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
