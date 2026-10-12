package com.alumni.tags;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.tags.common.BizException;
import com.alumni.tags.common.ErrorCode;
import com.alumni.tags.dto.TagDtos.TagItem;
import com.alumni.tags.entity.Tag;
import com.alumni.tags.entity.TagBinding;
import com.alumni.tags.repository.TagBindingRepository;
import com.alumni.tags.repository.TagRepository;
import com.alumni.tags.service.TagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TagService 单元测试（Mockito mock Repository / JdbcTemplate，无 HTTP / 无数据库）。
 *
 * <p>覆盖：创建（正常 + 审计 / 同域重名 409 / uk 并发兜底 409 / 角色 403 / 域非法 400）、
 * 改名（同域重名 409 / 不存在 404）、删除（引用中 409 RESOURCE_REFERENCED / 正常 + 审计）、
 * 绑定（超 20 上限 400 / 跨域 400 / 标签不存在 400 / 业务对象不存在 404 /
 * 角色 403 / 全量覆盖删旧插新 / 空数组解绑）、已绑查询、列表域校验。</p>
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    private static final AuthPrincipal SCHOOL = new AuthPrincipal(2L, null, "school_admin");
    private static final AuthPrincipal COLLEGE = new AuthPrincipal(3L, null, "college_admin");
    private static final AuthPrincipal STUDENT = new AuthPrincipal(101L, "2023002", "student");

    @Mock
    private TagRepository tagRepository;
    @Mock
    private TagBindingRepository tagBindingRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private TagService service;

    @BeforeEach
    void setUp() {
        service = new TagService(tagRepository, tagBindingRepository, jdbcTemplate, new ObjectMapper());
    }

    /* ==================== 创建 ==================== */

    /** 正常创建：createdBy 记为当前校级管理员，写审计 tag_change（detail 含 create 与标签名） */
    @Test
    void create_happyPath_writesAudit() {
        when(tagRepository.existsByTagGroupAndName("honor", "学霸")).thenReturn(false);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(inv -> {
            Tag t = inv.getArgument(0);
            t.setId(7L);
            return t;
        });

        TagItem item = service.create(SCHOOL, "学霸", "honor", 1);

        assertEquals(7L, item.id());
        assertEquals("学霸", item.name());
        assertEquals("honor", item.group());
        assertEquals(1, item.sort());

        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).saveAndFlush(tagCaptor.capture());
        assertEquals(2L, tagCaptor.getValue().getCreatedBy(), "创建人记为当前 school_admin");

        verifyAudit("create", "学霸", 7L);
    }

    /** 同域重名（先查命中）→ 409 TAG_DUPLICATED，且不触库写入 */
    @Test
    void create_duplicateInSameGroup_conflict() {
        when(tagRepository.existsByTagGroupAndName("honor", "学霸")).thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, "学霸", "honor", 1));
        assertEquals(ErrorCode.TAG_DUPLICATED, e.getErrorCode());
        verify(tagRepository, never()).saveAndFlush(any());
        verifyNoInteractions(jdbcTemplate);
    }

    /** 跨域同名允许：archive 域可建同名标签 */
    @Test
    void create_sameNameOtherGroup_allowed() {
        when(tagRepository.existsByTagGroupAndName("archive", "学霸")).thenReturn(false);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(inv -> {
            Tag t = inv.getArgument(0);
            t.setId(8L);
            return t;
        });

        TagItem item = service.create(SCHOOL, "学霸", "archive", 0);
        assertEquals("archive", item.group());
    }

    /** 并发同域重名：uk_tags_group_name 冲突 DataIntegrityViolationException 兜底 → 409 TAG_DUPLICATED */
    @Test
    void create_concurrentDuplicate_conflict() {
        when(tagRepository.existsByTagGroupAndName("honor", "学霸")).thenReturn(false);
        when(tagRepository.saveAndFlush(any(Tag.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_tags_group_name'"));

        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, "学霸", "honor", 1));
        assertEquals(ErrorCode.TAG_DUPLICATED, e.getErrorCode());
    }

    /** 非 school_admin（如 college_admin）→ 403，且不触库 */
    @Test
    void create_notSchoolAdmin_forbidden() {
        BizException e = assertThrows(BizException.class, () -> service.create(COLLEGE, "学霸", "honor", 1));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(tagRepository, jdbcTemplate);
    }

    /** group 非法取值 → 400 INVALID_PARAMETER */
    @Test
    void create_invalidGroup_badRequest() {
        BizException e = assertThrows(BizException.class, () -> service.create(SCHOOL, "学霸", "user", 1));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(tagRepository, jdbcTemplate);
    }

    /* ==================== 改名 / 调序 ==================== */

    /** 改名命中同域重名 → 409 TAG_DUPLICATED，且不保存 */
    @Test
    void update_renameDuplicated_conflict() {
        Tag tag = tag(7L, "学霸", "honor", 1);
        when(tagRepository.findById(7L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByTagGroupAndNameAndIdNot("honor", "学神", 7L)).thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> service.update(SCHOOL, 7L, "学神", null));
        assertEquals(ErrorCode.TAG_DUPLICATED, e.getErrorCode());
        verify(tagRepository, never()).saveAndFlush(any());
    }

    /** 正常改名 + 调序：保存并写审计 tag_change（detail 含 update 与新标签名） */
    @Test
    void update_happyPath_writesAudit() {
        Tag tag = tag(7L, "学霸", "honor", 1);
        when(tagRepository.findById(7L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByTagGroupAndNameAndIdNot("honor", "学神", 7L)).thenReturn(false);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        TagItem item = service.update(SCHOOL, 7L, "学神", 9);

        assertEquals("学神", item.name());
        assertEquals(9, item.sort());
        verifyAudit("update", "学神", 7L);
    }

    /** 标签不存在 → 404 NOT_FOUND */
    @Test
    void update_notFound() {
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.update(SCHOOL, 99L, "学神", null));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }

    /* ==================== 删除 ==================== */

    /** 被 tag_bindings 引用 → 409 RESOURCE_REFERENCED，且不删除 */
    @Test
    void delete_referenced_conflict() {
        Tag tag = tag(7L, "学霸", "honor", 1);
        when(tagRepository.findById(7L)).thenReturn(Optional.of(tag));
        when(tagBindingRepository.countByTagId(7L)).thenReturn(3L);

        BizException e = assertThrows(BizException.class, () -> service.delete(SCHOOL, 7L));
        assertEquals(ErrorCode.RESOURCE_REFERENCED, e.getErrorCode());
        verify(tagRepository, never()).delete(any(Tag.class));
        verifyNoInteractions(jdbcTemplate);
    }

    /** 正常删除：物理删除并写审计 tag_change（detail 含 delete 与标签名） */
    @Test
    void delete_happyPath_writesAudit() {
        Tag tag = tag(7L, "学霸", "honor", 1);
        when(tagRepository.findById(7L)).thenReturn(Optional.of(tag));
        when(tagBindingRepository.countByTagId(7L)).thenReturn(0L);

        service.delete(SCHOOL, 7L);

        verify(tagRepository).delete(tag);
        verifyAudit("delete", "学霸", 7L);
    }

    /* ==================== 绑定 ==================== */

    /** tag_ids 超过 20 → 400 BATCH_LIMIT_EXCEEDED，且不触库 */
    @Test
    void bind_overLimit_badRequest() {
        List<Long> tagIds = new ArrayList<>();
        for (long i = 1; i <= 21; i++) {
            tagIds.add(i);
        }

        BizException e = assertThrows(BizException.class,
                () -> service.bind(SCHOOL, "honor", 12L, tagIds));
        assertEquals(ErrorCode.BATCH_LIMIT_EXCEEDED, e.getErrorCode());
        verifyNoInteractions(tagRepository, tagBindingRepository, jdbcTemplate);
    }

    /** 绑定跨域标签（alumni 域标签绑 honor）→ 400 INVALID_PARAMETER，且不写绑定 */
    @Test
    void bind_crossDomainTag_badRequest() {
        when(tagRepository.findByIdIn(List.of(5L))).thenReturn(List.of(tag(5L, "学霸", "alumni", 1)));

        BizException e = assertThrows(BizException.class,
                () -> service.bind(SCHOOL, "honor", 12L, List.of(5L)));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verify(tagBindingRepository, never()).saveAll(any());
        verifyNoInteractions(jdbcTemplate);
    }

    /** 标签 id 不存在（findByIdIn 数量对不上）→ 400 INVALID_PARAMETER */
    @Test
    void bind_tagNotFound_badRequest() {
        when(tagRepository.findByIdIn(List.of(1L, 99L))).thenReturn(List.of(tag(1L, "学霸", "honor", 1)));

        BizException e = assertThrows(BizException.class,
                () -> service.bind(SCHOOL, "honor", 12L, List.of(1L, 99L)));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verify(tagBindingRepository, never()).saveAll(any());
    }

    /** 业务对象不存在（JdbcTemplate count = 0）→ 404 NOT_FOUND */
    @Test
    void bind_bizNotFound() {
        when(tagRepository.findByIdIn(List.of(1L))).thenReturn(List.of(tag(1L, "学霸", "honor", 1)));
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM honors WHERE id = ?", Long.class, 12L))
                .thenReturn(0L);

        BizException e = assertThrows(BizException.class,
                () -> service.bind(SCHOOL, "honor", 12L, List.of(1L)));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(tagBindingRepository, never()).saveAll(any());
    }

    /** 正常绑定：同事务删旧插新（幂等覆盖），返回按 sort,id 排序的最新已绑列表 */
    @Test
    @SuppressWarnings("unchecked")
    void bind_happyPath_replaceAll() {
        Tag t1 = tag(1L, "学霸", "honor", 5);
        Tag t5 = tag(5L, "竞赛达人", "honor", 1);
        when(tagRepository.findByIdIn(List.of(1L, 5L))).thenReturn(List.of(t1, t5));
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM honors WHERE id = ?", Long.class, 12L))
                .thenReturn(1L);

        List<TagItem> bound = service.bind(COLLEGE, "honor", 12L, List.of(1L, 5L));

        // 返回按 sort 升序：竞赛达人(1) 在 学霸(5) 前
        assertEquals(2, bound.size());
        assertEquals(5L, bound.get(0).id());
        assertEquals(1L, bound.get(1).id());

        verify(tagBindingRepository).deleteByBizTypeAndBizId("honor", 12L);
        ArgumentCaptor<Iterable<TagBinding>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(tagBindingRepository).saveAll(captor.capture());
        List<TagBinding> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(b -> "honor".equals(b.getBizType()) && b.getBizId() == 12L));
        assertEquals(List.of(1L, 5L), saved.stream().map(TagBinding::getTagId).toList());
    }

    /** 空 tag_ids = 解绑全部：仍删旧，插空集，返回空列表 */
    @Test
    void bind_emptyTagIds_unbindAll() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM archives WHERE id = ?", Long.class, 20L))
                .thenReturn(1L);

        List<TagItem> bound = service.bind(SCHOOL, "archive", 20L, List.of());

        assertTrue(bound.isEmpty());
        verify(tagBindingRepository).deleteByBizTypeAndBizId("archive", 20L);
        verify(tagBindingRepository).saveAll(List.of());
    }

    /** student 等非管理角色 → 403，且不触库 */
    @Test
    void bind_forbiddenRole() {
        BizException e = assertThrows(BizException.class,
                () -> service.bind(STUDENT, "honor", 12L, List.of(1L)));
        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(tagRepository, tagBindingRepository, jdbcTemplate);
    }

    /** 已绑查询：返回标签按 sort,id 排序 */
    @Test
    void bindings_sortedBySortAndId() {
        TagBinding b1 = binding(1L);
        TagBinding b2 = binding(5L);
        when(tagBindingRepository.findByBizTypeAndBizId("honor", 12L)).thenReturn(List.of(b1, b2));
        when(tagRepository.findByIdIn(List.of(1L, 5L)))
                .thenReturn(List.of(tag(1L, "学霸", "honor", 5), tag(5L, "竞赛达人", "honor", 1)));

        List<TagItem> items = service.bindings(STUDENT, "honor", 12L);

        assertEquals(2, items.size());
        assertEquals(5L, items.get(0).id(), "sort 小的在前");
        assertEquals(1L, items.get(1).id());
    }

    /** 列表：group 非法 → 400 INVALID_PARAMETER */
    @Test
    void page_invalidGroup_badRequest() {
        BizException e = assertThrows(BizException.class,
                () -> service.page(STUDENT, "user", null, 1, 20));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(tagRepository);
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private Tag tag(long id, String name, String group, int sort) {
        Tag t = new Tag();
        t.setId(id);
        t.setName(name);
        t.setTagGroup(group);
        t.setSort(sort);
        t.setCreatedBy(2L);
        return t;
    }

    private TagBinding binding(long tagId) {
        TagBinding b = new TagBinding();
        b.setTagId(tagId);
        b.setBizType("honor");
        b.setBizId(12L);
        return b;
    }

    /** 校验审计写入：audit_logs 插入 action='tag_change'，detail 含操作类型与标签名 */
    private void verifyAudit(String op, String name, long tagId) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> argCaptor = ArgumentCaptor.forClass(Object.class);
        // update(sql, user_id, user_role, biz_id, detail)：4 个绑定参数逐位捕获
        verify(jdbcTemplate).update(sqlCaptor.capture(), argCaptor.capture(), argCaptor.capture(),
                argCaptor.capture(), argCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("INSERT INTO audit_logs"));
        assertTrue(sqlCaptor.getValue().contains("'tag_change'"));
        List<Object> args = argCaptor.getAllValues();
        assertEquals(2L, args.get(0), "操作人 user_id");
        assertEquals("school_admin", args.get(1), "操作人角色");
        assertEquals(tagId, args.get(2), "biz_id 为标签 id");
        String detail = (String) args.get(3);
        assertTrue(detail.contains("\"op\":\"" + op + "\""), "detail 含操作类型 " + op);
        assertTrue(detail.contains("\"name\":\"" + name + "\""), "detail 含标签名");
    }
}
