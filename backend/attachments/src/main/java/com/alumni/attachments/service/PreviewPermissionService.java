package com.alumni.attachments.service;

import com.alumni.attachments.entity.Attachment;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * preview 权限继承（API 规范 6.2）：
 * - 已上架公开内容（showcase 存在）：免认证
 * - unreferenced：仅上传者本人
 * - referenced 且业务未审核（archives/honors 非 approved）：本人 + 审核角色
 * - referenced 且业务已 approved：任意登录用户
 * - 其余 biz_type（avatar/activate 等）：本人 + 审核角色
 */
@Service
public class PreviewPermissionService {

    private final JdbcTemplate jdbcTemplate;

    public PreviewPermissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public enum Decision { PUBLIC, ALLOW, NEED_LOGIN, DENY }

    public Decision decide(Attachment attachment, AuthPrincipal user) {
        // 大屏公开内容免认证（archive 已上架）
        if ("archive".equals(attachment.getBizType()) && attachment.getRefId() != null && isOnShowcase(attachment.getRefId())) {
            return Decision.PUBLIC;
        }
        if (user == null) {
            return Decision.NEED_LOGIN;
        }
        // 上传者本人与审核角色基础放行
        if (attachment.getUploaderId().equals(user.userId()) || user.isReviewer()) {
            return Decision.ALLOW;
        }
        if (attachment.getRefId() == null) {
            return Decision.DENY;
        }
        // referenced：按业务归属继承
        return switch (attachment.getBizType()) {
            case "archive" -> isApproved("archives", attachment.getRefId()) ? Decision.ALLOW : Decision.DENY;
            case "honor" -> isApproved("honors", attachment.getRefId()) ? Decision.ALLOW : Decision.DENY;
            default -> Decision.DENY;
        };
    }

    private boolean isOnShowcase(Long archiveId) {
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM showcase WHERE archive_id = ?", Long.class, archiveId);
        return cnt != null && cnt > 0;
    }

    private boolean isApproved(String table, Long refId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT status FROM " + table + " WHERE id = ?", String.class, refId);
        return !rows.isEmpty() && "approved".equals(rows.get(0));
    }
    // 注：table 取自代码内固定分支（archives/honors），不存在注入面
}
