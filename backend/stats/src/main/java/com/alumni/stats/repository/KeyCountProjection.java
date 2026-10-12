package com.alumni.stats.repository;

/**
 * 分组计数投影行：维度取值 k + 数量 cnt（荣誉按类别 / 级别聚合用）。
 */
public interface KeyCountProjection {

    String getK();

    Long getCnt();
}
