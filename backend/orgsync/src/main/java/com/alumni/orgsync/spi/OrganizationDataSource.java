package com.alumni.orgsync.spi;

import java.util.List;
import java.util.Map;

/**
 * 组织架构数据源 SPI（预留真实数据库接入；本期 MVP 用 MockAlumniBaseSource）。
 *
 * <p>实现约定：
 * <ul>
 *   <li>提供「当前外部系统的全量快照」数据流；不关心具体存储（库表/外部 API/CSV 导入）</li>
 *   <li>返回 List&lt;Map&gt;：每条记录必含外部主键 ext_id 与所有需同步字段</li>
 *   <li>异常必须向上抛出，由 OrgSyncService 捕获并写入 failures</li>
 * </ul>
 */
public interface OrganizationDataSource {

    /** 数据源名称（alumni_base / external_db / ...） */
    String name();

    /** 当前支持的同步类型（users / alumni_base / dicts） */
    List<String> supportedTypes();

    /**
     * 拉取全量快照。
     * @param type users / alumni_base / dicts
     * @return 记录列表；每条记录至少包含 ext_id 字段；字段名小写 snake_case
     */
    List<Map<String, Object>> fetchAll(String type);
}
