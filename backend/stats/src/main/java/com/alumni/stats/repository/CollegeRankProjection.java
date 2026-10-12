package com.alumni.stats.repository;

/**
 * 学院排行投影行（GET /api/v1/stats/colleges 出参行）。
 * college_id 来自 dicts（LEFT JOIN，学院文本未匹配字典时为 null）。
 */
public interface CollegeRankProjection {

    Long getCollegeId();

    String getCollegeName();

    Long getArchiveCount();
}
