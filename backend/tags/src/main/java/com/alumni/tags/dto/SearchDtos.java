package com.alumni.tags.dto;

import java.time.Instant;
import java.util.List;

/**
 * 增强检索 / 关键词联想出入参 DTO（全局 Jackson 已配置 snake_case）。
 */
public final class SearchDtos {

    private SearchDtos() {
    }

    /** 聚合检索结果项（honor/archive/alumni 三型统一结构，文档 3.3 节） */
    public record SearchItem(String type, Long id, String title, String owner,
                             String college, Integer gradYear, List<String> tags,
                             String status, Instant createdAt) {
    }

    /** 聚合检索分页包装：{list, total, page, page_size} */
    public record SearchResult(List<SearchItem> list, long total, int page, int pageSize) {
    }

    /** 联想词项：type 为 honor/tag/college/alumni，count 为命中记录数 */
    public record SuggestItem(String text, String type, long count) {
    }

    /** 联想响应包装 */
    public record SuggestResult(List<SuggestItem> list) {
    }
}
