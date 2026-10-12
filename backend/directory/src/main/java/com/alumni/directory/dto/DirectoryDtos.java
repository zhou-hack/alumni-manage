package com.alumni.directory.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 校友档案索引模块出入参 DTO（全局 Jackson 已配置 snake_case；
 * timeline/awards 为 JSON 透传结构，键名 year/event、title/year/level 原样保留）。
 */
public final class DirectoryDtos {

    private DirectoryDtos() {
    }

    /** GET /alumni/directory 列表项（公开名录检索） */
    public record DirectoryItem(String studentId, String name, String photoUrl, String college,
                                Integer gradYear, String current, String topHonor, long archiveCount) {
    }

    /** GET /alumni/directory/{student_id} 公开档案详情（两模块共用的弹窗数据） */
    public record DirectoryDetail(String studentId, String name, String photoUrl, String college,
                                  Integer gradYear, String current, String message,
                                  List<Map<String, String>> timeline, List<Map<String, String>> awards,
                                  List<PublicArchive> publicArchives) {
    }

    /** 详情内公开档案条目 */
    public record PublicArchive(Long id, String title, String category, Instant publishedAt) {
    }

    /** 分页包装（与 archives 模块 PageResult 格式一致） */
    public record PageResult(List<DirectoryItem> list, long total, int page, int pageSize) {
    }
}
