package com.alumni.admin.service;

import com.alumni.admin.common.BizException;
import com.alumni.admin.common.ErrorCode;
import com.alumni.admin.dto.AdminDtos.MatrixResult;
import com.alumni.admin.entity.AuditLog;
import com.alumni.admin.entity.FieldPermission;
import com.alumni.admin.repository.AuditLogRepository;
import com.alumni.admin.repository.FieldPermissionRepository;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段级权限（[M15] 角色—字段可见性矩阵）。
 *
 * <p>覆盖全部 6 角色 × 6 字段；未配置的 (role, field) 按预置默认值补齐：
 * phone → masked；id_card 对 student/alumni → hidden，其余 visible；其余字段 visible。</p>
 */
@Service
public class FieldPermissionService {

    /** 可配置角色白名单（6 角色） */
    public static final List<String> ROLES = List.of(
            "student", "alumni", "counselor", "college_admin", "school_admin", "system_admin");

    /** 可配置字段白名单（有序，矩阵输出稳定） */
    public static final List<String> FIELDS = List.of(
            "phone", "id_card", "email", "home_address", "work_place", "emergency_contact");

    /** 可见性取值 */
    public static final Set<String> VISIBILITIES = Set.of("visible", "masked", "hidden");

    private final FieldPermissionRepository fieldPermissionRepository;
    private final AuditLogRepository auditLogRepository;

    public FieldPermissionService(FieldPermissionRepository fieldPermissionRepository,
                                  AuditLogRepository auditLogRepository) {
        this.fieldPermissionRepository = fieldPermissionRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /** GET /admin/field-permissions —— 全量矩阵（默认值补齐） */
    @Transactional(readOnly = true)
    public MatrixResult matrix() {
        Map<String, Map<String, String>> roles = defaultMatrix();
        for (FieldPermission fp : fieldPermissionRepository.findAll()) {
            Map<String, String> fields = roles.get(fp.getRole());
            if (fields != null && FIELDS.contains(fp.getField())) {
                fields.put(fp.getField(), fp.getVisibility());
            }
        }
        return new MatrixResult(roles);
    }

    /** PUT /admin/field-permissions/{role} —— 按 (role, field) upsert，变更写审计 */
    @Transactional
    public Map<String, String> update(AuthPrincipal operator, String role, Map<String, String> fields) {
        if (!ROLES.contains(role)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "role 不在可配置范围：" + role);
        }
        if (fields == null || fields.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "fields 不能为空");
        }
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!FIELDS.contains(entry.getKey())) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "field 不在白名单：" + entry.getKey());
            }
            if (entry.getValue() == null || !VISIBILITIES.contains(entry.getValue())) {
                throw new BizException(ErrorCode.INVALID_PARAMETER,
                        "visibility 非法（visible/masked/hidden）：" + entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            FieldPermission fp = fieldPermissionRepository
                    .findByRoleAndField(role, entry.getKey())
                    .orElseGet(() -> {
                        FieldPermission created = new FieldPermission();
                        created.setRole(role);
                        created.setField(entry.getKey());
                        return created;
                    });
            fp.setVisibility(entry.getValue());
            fp.setUpdatedBy(operator.userId());
            fieldPermissionRepository.save(fp);
        }

        AuditLog audit = new AuditLog();
        audit.setUserId(operator.userId());
        audit.setUserRole(operator.role());
        audit.setAction("field_permission_change");
        audit.setBizType("field_permission");
        audit.setDetail(Map.of("role", role, "fields", fields));
        auditLogRepository.save(audit);

        return matrix().roles().get(role);
    }

    /** 预置默认矩阵：phone 全角色 masked；id_card 对 student/alumni hidden；其余 visible */
    private Map<String, Map<String, String>> defaultMatrix() {
        Map<String, Map<String, String>> roles = new LinkedHashMap<>();
        for (String role : ROLES) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (String field : FIELDS) {
                fields.put(field, defaultVisibility(role, field));
            }
            roles.put(role, fields);
        }
        return roles;
    }

    private String defaultVisibility(String role, String field) {
        if ("phone".equals(field)) {
            return "masked";
        }
        if ("id_card".equals(field) && ("student".equals(role) || "alumni".equals(role))) {
            return "hidden";
        }
        return "visible";
    }
}
