package com.alumni.orgsync.spi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock 数据源：直接读取 alumni_base 表（视校友库为「组织数据源」）。
 * 真实数据库接入时，新建一个实现 OrganizationDataSource 的 @Component 替换即可。
 */
@Component
public class AlumniBaseDataSource implements OrganizationDataSource {

    private final JdbcTemplate jdbcTemplate;

    public AlumniBaseDataSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String name() {
        return "alumni_base";
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("alumni_base");
    }

    @Override
    public List<Map<String, Object>> fetchAll(String type) {
        if (!"alumni_base".equals(type)) {
            throw new IllegalArgumentException("不支持的同步类型: " + type);
        }
        return jdbcTemplate.query(
                "SELECT id AS ext_id, student_id, real_name, phone, college, major, grad_year, id_card " +
                "FROM alumni_base",
                (rs, idx) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("ext_id", rs.getLong("ext_id"));
                    row.put("student_id", rs.getString("student_id"));
                    row.put("real_name", rs.getString("real_name"));
                    row.put("phone", rs.getString("phone"));
                    row.put("college", rs.getString("college"));
                    row.put("major", rs.getString("major"));
                    row.put("grad_year", rs.getObject("grad_year"));
                    row.put("id_card", rs.getString("id_card"));
                    return row;
                });
    }
}
