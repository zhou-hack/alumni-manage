package com.alumni.dicts.controller;

import com.alumni.dicts.common.ApiResponse;
import com.alumni.dicts.dto.DictDtos.CreateDictRequest;
import com.alumni.dicts.dto.DictDtos.DictItemVO;
import com.alumni.dicts.dto.DictDtos.UpdateDictRequest;
import com.alumni.dicts.service.DictService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 基础字典接口（Base URL: /api/v1，见 API 规范第三章）。
 * GET 登录即可；写操作仅 school_admin（由 SecurityConfig 拦截）。
 */
@RestController
@RequestMapping("/api/v1/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /** 按类型取字典项：college / major / grad_year / honor_category；支持 ?parent_id= 按父级过滤（如查某学院下的专业） */
    @GetMapping("/{type}")
    public ApiResponse<List<DictItemVO>> list(@PathVariable String type,
                                              @RequestParam(value = "parent_id", required = false) Long parentId) {
        return ApiResponse.ok(dictService.list(type, parentId));
    }

    /** 新增字典项（school_admin） */
    @PostMapping("/{type}")
    public ApiResponse<DictItemVO> create(@PathVariable String type,
                                          @Valid @RequestBody CreateDictRequest req) {
        return ApiResponse.ok(dictService.create(type, req));
    }

    /** 修改字典项（school_admin） */
    @PatchMapping("/{type}/{id}")
    public ApiResponse<DictItemVO> update(@PathVariable String type, @PathVariable Long id,
                                          @Valid @RequestBody UpdateDictRequest req) {
        return ApiResponse.ok(dictService.update(type, id, req));
    }

    /** 删除字典项（school_admin；被引用 409 RESOURCE_REFERENCED） */
    @DeleteMapping("/{type}/{id}")
    public ApiResponse<Void> delete(@PathVariable String type, @PathVariable Long id) {
        dictService.delete(type, id);
        return ApiResponse.ok(null);
    }
}
