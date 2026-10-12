package com.alumni.honors.service;

import com.alumni.common.pinyin.PinyinUtils;
import com.alumni.honors.dto.HonorTeamDtos.Candidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * [M03] 人名匹配核心：团队荣誉成员名单 → 校友库（alumni_base）识别。
 *
 * <p>匹配策略（确定性优先，杜绝错挂他人）：</p>
 * <ol>
 *   <li>本院精确：real_name 精确相等 + 提交者本院 + 已激活账号 → 唯一命中即 matched</li>
 *   <li>全校精确：去掉学院条件 → 唯一命中即 matched（跨院）；多条命中 → ambiguous（不自动建荣誉）</li>
 *   <li>无账号：real_name 命中校友库但无 active 账号 → no_account（无法挂载荣誉）</li>
 *   <li>拼音候选：仅作 unmatched 的建议返回（容错生僻字/错别字），绝不自动匹配</li>
 * </ol>
 *
 * <p>跨模块数据（alumni_base/users）经共享库 JdbcTemplate 直查，与模块内 COVERAGE_SQL 模式一致。</p>
 */
@Service
public class HonorMatchService {

    /** 人名解析分隔符：顿号/逗号（中英）/分号（中英）/斜杠/空白 */
    private static final String NAME_SEPARATORS = "[、，,;；/\\s]+";
    /** 人名长度约束（对齐 alumni_base.real_name VARCHAR(64)） */
    private static final int NAME_MIN_LEN = 2;
    private static final int NAME_MAX_LEN = 64;
    /** 拼音候选建议上限 */
    private static final int CANDIDATE_LIMIT = 5;

    private static final String EXACT_MATCH_SQL = """
            SELECT ab.id AS alumni_base_id, ab.student_id, ab.real_name, ab.college, u.id AS user_id
            FROM alumni_base ab
            JOIN users u ON u.alumni_base_id = ab.id AND u.status = 'active'
                         AND u.role IN ('student', 'alumni')
            WHERE ab.real_name = ?
            """;

    private static final String PINYIN_CANDIDATE_SQL = """
            SELECT ab.student_id, ab.real_name, ab.college
            FROM alumni_base ab
            WHERE ab.real_name_pinyin LIKE CONCAT(?, '%')
               OR ab.real_name_pinyin_abbr = ?
            LIMIT ?
            """;

    /** 单个候选行（精确匹配链路，含激活账号） */
    public record ExactMatch(Long alumniBaseId, String studentId, String realName,
                             String college, Long userId) {
    }

    /** 拼音候选行（仅建议） */
    public record PinyinCandidate(String studentId, String realName, String college) {
    }

    private final JdbcTemplate jdbcTemplate;

    public HonorMatchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 解析成员名单文本：按分隔符切分 → trim → 过滤空与非法长度 → LinkedHashSet 去重保序 */
    public LinkedHashSet<String> parseNames(String memberNames) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (memberNames == null || memberNames.isBlank()) {
            return names;
        }
        for (String token : memberNames.split(NAME_SEPARATORS)) {
            String name = token.trim();
            if (name.length() >= NAME_MIN_LEN && name.length() <= NAME_MAX_LEN) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 精确匹配：先本院（提交者学院）后全校，均要求已激活账号。
     *
     * @return 唯一命中 → ExactMatch；多条命中 → null 且 candidates 非空（ambiguous）；
     *         校友库有此姓名但无激活账号 → NoAccount 语义（调用方按空结果+库内存在判定）
     */
    public List<ExactMatch> exactMatch(String name, String collegeLabel) {
        // ① 本院精确（唯一命中即返回）
        List<ExactMatch> inCollege = queryExact(name, collegeLabel);
        if (inCollege.size() == 1) {
            return inCollege;
        }
        if (inCollege.size() > 1) {
            return inCollege; // 本院重名 → ambiguous
        }
        // ② 全校精确
        return queryExact(name, null);
    }

    /** 姓名是否存在于校友库（无论是否激活）——no_account 判定用 */
    public boolean nameExistsInBase(String name) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alumni_base WHERE real_name = ?", Integer.class, name);
        return cnt != null && cnt > 0;
    }

    /** 校友库（未激活）命中行：alumni_base.id + 学号——no_account 留证与响应用 */
    public List<BaseMatch> baseMatches(String name) {
        return jdbcTemplate.query(
                "SELECT id, student_id FROM alumni_base WHERE real_name = ? LIMIT 2",
                (rs, i) -> new BaseMatch(rs.getLong("id"), rs.getString("student_id")),
                name);
    }

    /** 校友库命中行（仅 id 与学号） */
    public record BaseMatch(Long alumniBaseId, String studentId) {
    }

    /** 拼音候选建议（全拼前缀 / 首字母精确）：仅建议不自动匹配 */
    public List<Candidate> pinyinCandidates(String name) {
        String full = PinyinUtils.toFullPinyin(name);
        String abbr = PinyinUtils.toAbbr(name);
        if ((full == null || full.isBlank()) && (abbr == null || abbr.isBlank())) {
            return List.of();
        }
        return jdbcTemplate.query(PINYIN_CANDIDATE_SQL,
                (rs, i) -> new Candidate(rs.getString("student_id"), rs.getString("real_name"),
                        rs.getString("college")),
                full, abbr, CANDIDATE_LIMIT);
    }

    private List<ExactMatch> queryExact(String name, String collegeLabel) {
        String sql = collegeLabel == null
                ? EXACT_MATCH_SQL
                : EXACT_MATCH_SQL + " AND ab.college = ?";
        Object[] args = collegeLabel == null
                ? new Object[]{name}
                : new Object[]{name, collegeLabel};
        return jdbcTemplate.query(sql,
                (rs, i) -> new ExactMatch(rs.getLong("alumni_base_id"), rs.getString("student_id"),
                        rs.getString("real_name"), rs.getString("college"), rs.getLong("user_id")),
                args);
    }

    /** 候选列表转 DTO（歧义/未匹配建议共用；附学号便于人工消歧） */
    public List<Candidate> toCandidates(List<ExactMatch> matches) {
        return matches.stream()
                .limit(CANDIDATE_LIMIT)
                .map(m -> new Candidate(m.studentId(), m.realName(), m.college()))
                .toList();
    }
}
