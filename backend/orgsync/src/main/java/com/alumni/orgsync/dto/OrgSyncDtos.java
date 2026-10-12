package com.alumni.orgsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public final class OrgSyncDtos {
    private OrgSyncDtos() {}

    /** 触发同步 */
    public record SyncRequest(
            @NotBlank @Pattern(regexp = "^(users|alumni_base|dicts)$",
                    message = "sync_type 仅支持 users / alumni_base / dicts") String syncType,
            @Pattern(regexp = "^(incremental|full)$",
                    message = "mode 仅支持 incremental / full") String mode,
            String remark) {
    }

    /** 同步结果（响应 data） */
    public record SyncResult(Long logId, String status, int total, int inserted, int updated, int skipped, int failed) {
    }

    /** 同步日志列表项 */
    public record LogItem(
            Long id,
            String source,
            String syncType,
            String mode,
            Integer total,
            Integer inserted,
            Integer updated,
            Integer skipped,
            Integer failed,
            String status,
            String remark,
            Long operatorId,
            Instant startedAt,
            Instant finishedAt) {
    }

    public record PageResult<T>(List<T> list, long total, int page, int pageSize) {
    }
}
