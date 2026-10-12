package com.alumni.showcase.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.showcase.common.BizException;
import com.alumni.showcase.common.ErrorCode;
import com.alumni.showcase.dto.AlumniProfileDtos.ProfileRequest;
import com.alumni.showcase.dto.AlumniProfileDtos.ProfileResponse;
import com.alumni.showcase.entity.AlumniBase;
import com.alumni.showcase.repository.AlumniBaseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 校友大屏展示字段管理（V18 · photo / industry / intro / quote / motto）。
 *
 * <p>权限：
 * <ul>
 *   <li>校级 / 系统管理员：可改任意校友（路径 /api/v1/admin/alumni/{id}/profile）</li>
 *   <li>校友本人：可改自己的档案（路径 /api/v1/me/profile）</li>
 *   <li>其他人：只读</li>
 * </ul>
 */
@Service
public class AlumniProfileService {

    /** 可写角色（仅校级/系统管理员） */
    private static final Set<String> MANAGER_ROLES = Set.of("school_admin", "system_admin");

    /** 写权限校友侧角色（校友本人 / 在校学生） */
    private static final Set<String> SELF_ROLES = Set.of("alumni", "student");

    private final AlumniBaseRepository repo;

    @PersistenceContext
    private EntityManager em;

    public AlumniProfileService(AlumniBaseRepository repo) {
        this.repo = repo;
    }

    /** 任意角色（含未登录）可读：用于公开校友风采 / 大屏聚合 */
    @Transactional(readOnly = true)
    public ProfileResponse get(long alumniId) {
        // 用原生 SQL 跨模块读 V18 字段（其他模块的 AlumniBase 实体不声明这些列）
        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT id, real_name, student_id, college, grad_year, " +
                "       photo_url, industry, intro, quote, motto " +
                "FROM alumni_base WHERE id = ?1"
        ).setParameter(1, alumniId).getSingleResult();

        return new ProfileResponse(
                ((Number) row[0]).longValue(),
                str(row[1]),
                str(row[2]),
                str(row[3]),
                row[4] == null ? null : ((Number) row[4]).intValue(),
                str(row[5]),
                str(row[6]),
                str(row[7]),
                str(row[8]),
                str(row[9]),
                System.currentTimeMillis()
        );
    }

    /** 校级/系统管理员：编辑任意校友档案 */
    @Transactional
    public ProfileResponse updateByAdmin(AuthPrincipal operator, long alumniId, ProfileRequest req) {
        requireRole(operator, MANAGER_ROLES, "仅校级或系统管理员可编辑校友档案");
        return updateInternal(alumniId, req);
    }

    /** 校友本人：编辑自己（user.alumni_base_id == alumniId） */
    @Transactional
    public ProfileResponse updateBySelf(AuthPrincipal operator, ProfileRequest req) {
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        if (!SELF_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅校友/学生本人可编辑自己的档案");
        }
        if (operator.alumniBaseId() == null) {
            throw new BizException(ErrorCode.INVALID_STATE, "当前账号未绑定校友库 ID，请联系管理员");
        }
        return updateInternal(operator.alumniBaseId(), req);
    }

    private ProfileResponse updateInternal(long alumniId, ProfileRequest req) {
        // 校验校友存在（avoid 静默 0 行更新）
        Object exists = em.createNativeQuery("SELECT 1 FROM alumni_base WHERE id = ?1")
                .setParameter(1, alumniId)
                .getResultStream().findAny().orElse(null);
        if (exists == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "校友不存在");
        }

        int affected = repo.updateProfileFields(
                alumniId,
                normalize(req.photoUrl()),
                normalize(req.industry()),
                normalize(req.intro()),
                normalize(req.quote()),
                normalize(req.motto())
        );
        if (affected == 0) {
            throw new BizException(ErrorCode.INVALID_STATE, "更新失败（0 行受影响）");
        }
        return get(alumniId);
    }

    /** null → 保留旧值（前端用空字符串覆盖时显式清空） */
    private static String normalize(String s) {
        return s == null ? null : s.trim();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private void requireRole(AuthPrincipal operator, Set<String> roles, String msg) {
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        if (!roles.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, msg);
        }
    }
}
