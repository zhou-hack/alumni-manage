package com.alumni.showcase.repository;

/**
 * 大屏轮播数据投影（GET /api/v1/screen/feed 出参行）。
 */
public interface FeedProjection {

    Long getId();

    String getTitle();

    String getCategory();

    String getOwner();

    String getCollege();

    Integer getGradYear();

    String getCoverUrl();
}
