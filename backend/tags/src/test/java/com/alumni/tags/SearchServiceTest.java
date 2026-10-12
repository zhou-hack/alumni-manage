package com.alumni.tags;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.tags.common.BizException;
import com.alumni.tags.common.ErrorCode;
import com.alumni.tags.dto.SearchDtos.SearchItem;
import com.alumni.tags.dto.SearchDtos.SearchResult;
import com.alumni.tags.dto.SearchDtos.SuggestItem;
import com.alumni.tags.dto.SearchDtos.SuggestResult;
import com.alumni.tags.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SearchService 单元测试（Mockito mock JdbcTemplate，无 HTTP / 无数据库）。
 *
 * <p>覆盖：拼音/首字母检索参数拼装（原词 + 全拼 + 首字母三形态）、数据范围红线
 * （student/alumni 强制 approved，传 include_all 也忽略；审核角色 include_all=true 放行在审）、
 * tag_ids / college_id / grad_year 过滤参数、types/tag_ids 非法 400、
 * 三型结果内存合并按 created_at 倒序分页（total = 各型 count 之和）、
 * 联想（student 不出姓名联想 / school_admin 出姓名联想 / limit 上限 20 / 拼音参数）。</p>
 *
 * <p>说明：JdbcTemplate 查询方法为 varargs 签名（Mockito 严格 varargs 匹配），
 * 这里用手工 mock + 默认 Answer 捕获全部调用（SQL 自参数索引 0、绑定参数自索引 2 起展开）。</p>
 */
class SearchServiceTest {

    private static final AuthPrincipal STUDENT = new AuthPrincipal(101L, "2023002", "student");
    private static final AuthPrincipal ALUMNI = new AuthPrincipal(100L, "2021001", "alumni");
    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");

    private JdbcTemplate jdbcTemplate;
    private SearchService service;

    /** 捕获所有 count 查询的 SQL 与绑定参数 */
    private List<String> countSqls;
    private List<Object[]> countParams;
    /** 捕获所有明细/联想查询的 SQL 与绑定参数 */
    private List<String> listSqls;
    private List<Object[]> listParams;

    /** 按 SQL 定制 count 返回值（默认 0） */
    private Function<String, Long> countAnswer;
    /** 按 SQL 定制明细/联想返回值（默认空列表） */
    private Function<String, List<?>> listAnswer;

    @BeforeEach
    void setUp() {
        countSqls = new ArrayList<>();
        countParams = new ArrayList<>();
        listSqls = new ArrayList<>();
        listParams = new ArrayList<>();
        countAnswer = sql -> 0L;
        listAnswer = sql -> List.of();
        jdbcTemplate = Mockito.mock(JdbcTemplate.class, inv -> {
            Object[] args = inv.getArguments();
            String sql = (String) args[0];
            String method = inv.getMethod().getName();
            if ("queryForObject".equals(method)) {
                countSqls.add(sql);
                countParams.add(Arrays.copyOfRange(args, 2, args.length));
                return countAnswer.apply(sql);
            }
            if ("query".equals(method)) {
                listSqls.add(sql);
                listParams.add(Arrays.copyOfRange(args, 2, args.length));
                return listAnswer.apply(sql);
            }
            return Answers.RETURNS_DEFAULTS.answer(inv);
        });
        service = new SearchService(jdbcTemplate);
    }

    /* ==================== 拼音参数拼装 ==================== */

    /** keyword=张三：传入 JdbcTemplate 的参数同时含原词、全拼、首字母三形态（%张三% / %zhangsan% / %zs%） */
    @Test
    void search_pinyinParams_assembled() {
        service.search(ALUMNI, "张三", "honor", null, null, null, false, 1, 20);

        assertEquals(1, listSqls.size());
        assertTrue(listSqls.get(0).contains("h.title_pinyin"), "SQL 命中拼音列");
        assertTrue(listSqls.get(0).contains("h.title_pinyin_abbr"), "SQL 命中首字母列");
        List<Object> params = Arrays.asList(listParams.get(0));
        assertTrue(params.contains("%张三%"), "含原词模糊");
        assertTrue(params.contains("%zhangsan%"), "含全拼模糊");
        assertTrue(params.contains("%zs%"), "含首字母模糊");
    }

    /** alumni 类型：keyword 三形态绑定在 alumni_base.real_name 及其拼音列上 */
    @Test
    void search_alumniType_pinyinParamsOnRealName() {
        service.search(ALUMNI, "张三", "alumni", null, null, null, false, 1, 20);

        assertTrue(listSqls.get(0).contains("ab.real_name_pinyin"), "SQL 命中姓名拼音列");
        List<Object> params = Arrays.asList(listParams.get(0));
        assertTrue(params.contains("%张三%"));
        assertTrue(params.contains("%zhangsan%"));
        assertTrue(params.contains("%zs%"));
    }

    /* ==================== 数据范围红线 ==================== */

    /** student 即使传 include_all=true 也强制 approved（honor/archive）+ active（alumni） */
    @Test
    void search_studentIncludeAllIgnored_approvedOnly() {
        service.search(STUDENT, null, null, null, null, null, true, 1, 20);

        assertEquals(3, listSqls.size(), "默认检索三种类型");
        String honorSql = sqlOf(listSqls, "FROM honors");
        String archiveSql = sqlOf(listSqls, "FROM archives");
        String alumniSql = sqlOf(listSqls, "FROM users");
        assertTrue(honorSql.contains("h.status = 'approved'"), "student 仅见 approved 荣誉");
        assertTrue(archiveSql.contains("a.status = 'approved'"), "student 仅见 approved 档案");
        assertTrue(alumniSql.contains("u.status = 'active'"), "student 仅见正常状态校友");
    }

    /** school_admin 传 include_all=true：可含在审数据（不再附加 approved 过滤） */
    @Test
    void search_schoolAdminIncludeAll_noApprovedFilter() {
        service.search(SCHOOL, null, "honor", null, null, null, true, 1, 20);

        assertFalse(listSqls.get(0).contains("status = 'approved'"), "include_all 放行在审数据");
    }

    /** school_admin 不传 include_all：默认仍仅 approved */
    @Test
    void search_schoolAdminDefault_approvedOnly() {
        service.search(SCHOOL, null, "honor", null, null, null, false, 1, 20);

        assertTrue(listSqls.get(0).contains("h.status = 'approved'"));
    }

    /* ==================== 过滤参数 ==================== */

    /** tag_ids 过滤：EXISTS tag_bindings 子查询 + 任一标签命中（IN 绑定） */
    @Test
    void search_tagIdsFilter_existsSubquery() {
        service.search(ALUMNI, null, "honor", null, null, "1,5", false, 1, 20);

        String sql = listSqls.get(0);
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM tag_bindings"), "tag_ids 走 tag_bindings 子查询");
        assertTrue(sql.contains("tbx.tag_id IN (?, ?)"));
        List<Object> params = Arrays.asList(listParams.get(0));
        assertTrue(params.contains(1L) && params.contains(5L));
    }

    /** college_id → dicts.label 子查询对齐 alumni_base.college；grad_year 等值过滤 */
    @Test
    void search_collegeAndGradYear_params() {
        service.search(ALUMNI, null, "alumni", 3L, 2025, null, false, 1, 20);

        String sql = listSqls.get(0);
        assertTrue(sql.contains("ab.college = (SELECT label FROM dicts WHERE id = ? AND type = 'college')"));
        assertTrue(sql.contains("ab.grad_year = ?"));
        List<Object> params = Arrays.asList(listParams.get(0));
        assertTrue(params.contains(3L), "college_id 入参");
        assertTrue(params.contains(2025), "grad_year 入参");
    }

    /** types 非法取值 → 400 INVALID_PARAMETER，且不触库 */
    @Test
    void search_invalidType_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.search(ALUMNI, null, "honor,bad", null, null, null, false, 1, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(countSqls.isEmpty() && listSqls.isEmpty(), "非法参数不触库");
    }

    /** tag_ids 含非数字 → 400 INVALID_PARAMETER */
    @Test
    void search_invalidTagIds_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.search(ALUMNI, null, "honor", null, null, "1,x", false, 1, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(countSqls.isEmpty() && listSqls.isEmpty(), "非法参数不触库");
    }

    /* ==================== 合并分页 ==================== */

    /** 三型结果按 created_at 倒序内存合并；total = 各型 count 之和；页切片正确 */
    @Test
    void search_mergeOrderAndTotal() {
        countAnswer = sql -> sql.contains("FROM honors") ? 2L : 1L;
        SearchItem honorOlder = item("honor", 1L, "国家奖学金", "2026-09-17T02:56:00Z");
        SearchItem honorNewer = item("honor", 3L, "竞赛金奖", "2026-09-19T02:56:00Z");
        SearchItem archiveMid = item("archive", 2L, "支教经历", "2026-09-18T02:56:00Z");
        listAnswer = sql -> {
            if (sql.contains("FROM honors")) {
                return List.of(honorNewer, honorOlder);
            }
            if (sql.contains("FROM archives")) {
                return List.of(archiveMid);
            }
            return List.of();
        };

        SearchResult page1 = service.search(ALUMNI, null, "honor,archive", null, null, null, false, 1, 2);

        assertEquals(3L, page1.total(), "total = 各型 count 之和");
        assertEquals(2, page1.list().size());
        assertEquals(3L, page1.list().get(0).id(), "最新 created_at 在前");
        assertEquals(2L, page1.list().get(1).id());

        SearchResult page2 = service.search(ALUMNI, null, "honor,archive", null, null, null, false, 2, 2);
        assertEquals(3L, page2.total());
        assertEquals(1, page2.list().size());
        assertEquals(1L, page2.list().get(0).id());
    }

    /* ==================== 联想 ==================== */

    /** student 联想：仅 honor/tag/college 三来源，不出校友姓名；荣誉标题仅 approved */
    @Test
    void suggest_student_noAlumniNames() {
        SuggestResult result = service.suggest(STUDENT, "国家", 10);

        assertEquals(3, listSqls.size(), "student 仅三个联想来源");
        assertTrue(listSqls.stream().noneMatch(sql -> sql.contains("alumni_base")),
                "student/alumni/counselor 不出姓名联想");
        String honorSql = sqlOf(listSqls, "FROM honors");
        assertTrue(honorSql.contains("h.status = 'approved'"), "student 联想仅 approved 荣誉");
        assertNotNull(result);
    }

    /** school_admin 联想：含校友姓名来源，荣誉标题不限 approved */
    @Test
    void suggest_schoolAdmin_includesAlumniNames() {
        service.suggest(SCHOOL, "张", 10);

        assertEquals(4, listSqls.size(), "审核角色四个联想来源（含校友姓名）");
        assertTrue(listSqls.stream().anyMatch(sql -> sql.contains("alumni_base")),
                "审核角色出姓名联想");
        String honorSql = sqlOf(listSqls, "FROM honors");
        assertFalse(honorSql.contains("status = 'approved'"), "审核角色联想不限 approved");
    }

    /** 联想 limit 超 20 截断为 20（传入各来源查询的 LIMIT 参数为 20） */
    @Test
    void suggest_limitClampedTo20() {
        service.suggest(SCHOOL, "张", 50);

        assertEquals(4, listParams.size());
        for (Object[] params : listParams) {
            assertEquals(20, params[params.length - 1], "LIMIT 参数截断为 20");
        }
    }

    /** 联想拼音参数：keyword=张 → 原词 + 全拼 + 首字母三形态 */
    @Test
    void suggest_pinyinParams() {
        service.suggest(STUDENT, "张", 10);

        String honorSql = sqlOf(listSqls, "FROM honors");
        assertTrue(honorSql.contains("h.title_pinyin"));
        int honorIndex = listSqls.indexOf(honorSql);
        List<Object> params = Arrays.asList(listParams.get(honorIndex));
        assertTrue(params.contains("%张%"));
        assertTrue(params.contains("%zhang%"));
        assertTrue(params.contains("%z%"));
    }

    /** 联想结果聚合：count 透传，整体按 limit 截断 */
    @Test
    void suggest_mergeAndTruncate() {
        listAnswer = sql -> {
            if (sql.contains("FROM honors")) {
                return List.of(new SuggestItem("国家奖学金", "honor", 37),
                        new SuggestItem("国家励志奖学金", "honor", 12));
            }
            if (sql.contains("FROM tags")) {
                return List.of(new SuggestItem("国家级", "tag", 3));
            }
            return List.of();
        };

        SuggestResult result = service.suggest(SCHOOL, "国家", 2);

        assertEquals(2, result.list().size(), "整体按 limit 截断");
        assertEquals("国家奖学金", result.list().get(0).text());
        assertEquals(37L, result.list().get(0).count());
        assertEquals("honor", result.list().get(0).type());
    }

    /** keyword 空白 → 400 INVALID_PARAMETER */
    @Test
    void suggest_blankKeyword_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.suggest(STUDENT, " ", 10));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        assertTrue(countSqls.isEmpty() && listSqls.isEmpty(), "非法参数不触库");
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private SearchItem item(String type, long id, String title, String createdAt) {
        return new SearchItem(type, id, title, "张三", "计算机学院", 2025, List.of(),
                "approved", Instant.parse(createdAt));
    }

    /** 在捕获的 SQL 列表中找含指定片段的第一条 */
    private static String sqlOf(List<String> sqls, String fragment) {
        return sqls.stream().filter(sql -> sql.contains(fragment)).findFirst()
                .orElseThrow(() -> new AssertionError("未捕获到含 " + fragment + " 的 SQL"));
    }
}
