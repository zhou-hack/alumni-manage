package com.alumni.teachers.sync;

import com.alumni.teachers.common.BizException;
import com.alumni.teachers.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 内网 REST 教师数据源（mock=false 时默认装配）。
 * 响应兼容三种形态：顶层 JSON 数组 / {data:[...]} / {list:[...]} / {records:[...]}。
 * url 未配置或拉取失败抛 UPSTREAM_ERROR，由同步流程记失败、保留本地数据。
 */
@Component
@ConditionalOnProperty(name = "app.teachers.sync.mock", havingValue = "false", matchIfMissing = true)
public class RestTeacherDataSource implements TeacherDataSource {

    private static final Logger log = LoggerFactory.getLogger(RestTeacherDataSource.class);

    private final TeacherSyncProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestTeacherDataSource(TeacherSyncProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchTeachers() {
        String url = properties.getUrl();
        if (url == null || url.isBlank()) {
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "未配置内网教师数据接口地址（app.teachers.sync.url）");
        }
        RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(url);
        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            spec = spec.header("Authorization", "Bearer " + properties.getToken().trim());
        }
        Object body;
        try {
            body = spec.retrieve().body(Object.class);
        } catch (Exception e) {
            log.warn("内网教师接口拉取失败：url={}", url, e);
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "内网教师接口拉取失败：" + e.getMessage());
        }
        List<Map<String, Object>> rows = extractRows(body);
        if (rows == null) {
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "内网教师接口响应格式不支持（期望数组或 data/list/records 包裹）");
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Object body) {
        if (body instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        if (body instanceof Map<?, ?> map) {
            for (String key : new String[]{"data", "list", "records"}) {
                Object nested = map.get(key);
                if (nested instanceof List<?>) {
                    return (List<Map<String, Object>>) nested;
                }
            }
        }
        return null;
    }
}
