package com.alumni.stats;

import com.alumni.stats.common.BizException;
import com.alumni.stats.common.ErrorCode;
import com.alumni.stats.repository.ArchiveRepository;
import com.alumni.stats.repository.CollegeRankProjection;
import com.alumni.stats.repository.CollegeRankRepository;
import com.alumni.stats.repository.HonorDistributionRepository;
import com.alumni.stats.repository.HonorRepository;
import com.alumni.stats.repository.KeyCountProjection;
import com.alumni.stats.repository.ReviewRepository;
import com.alumni.stats.repository.ShowcaseCountRepository;
import com.alumni.stats.repository.UserRepository;
import com.alumni.stats.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StatsService 单元测试（Mockito mock Repository，无 HTTP / 无数据库）。
 *
 * <p>覆盖：总量看板四项计数组装、荣誉分布双维度聚合、
 * limit 默认值（8）/ 边界值（1、100）/ 非法值（0、101、负数）校验、
 * 学院排行结果透传。</p>
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ArchiveRepository archiveRepository;
    @Mock
    private HonorRepository honorRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private CollegeRankRepository collegeRankRepository;
    @Mock
    private ShowcaseCountRepository showcaseCountRepository;
    @Mock
    private HonorDistributionRepository honorDistributionRepository;

    private StatsService service;

    @BeforeEach
    void setUp() {
        service = new StatsService(userRepository, archiveRepository, honorRepository,
                reviewRepository, collegeRankRepository, showcaseCountRepository,
                honorDistributionRepository);
    }

    /** 总量看板：五个计数正确组装为 snake_case 出参 */
    @Test
    void overview_returnsAllCounts() {
        when(userRepository.count()).thenReturn(1000L);
        when(archiveRepository.count()).thenReturn(320L);
        when(honorRepository.count()).thenReturn(150L);
        when(reviewRepository.countByStatus("pending")).thenReturn(12L);
        when(showcaseCountRepository.countAll()).thenReturn(5L);

        Map<String, Object> data = service.overview();

        assertEquals(1000L, data.get("total_alumni"), "校友数");
        assertEquals(320L, data.get("total_archives"), "档案数");
        assertEquals(150L, data.get("total_honors"), "荣誉数");
        assertEquals(12L, data.get("pending_reviews"), "待审数");
        assertEquals(5L, data.get("published_count"), "已上架数");
    }

    /** 待审数口径：必须按 reviews.status='pending' 计数 */
    @Test
    void overview_pendingReviews_queriesPendingStatus() {
        when(userRepository.count()).thenReturn(0L);
        when(archiveRepository.count()).thenReturn(0L);
        when(honorRepository.count()).thenReturn(0L);
        when(reviewRepository.countByStatus("pending")).thenReturn(7L);

        Map<String, Object> data = service.overview();

        assertEquals(7L, data.get("pending_reviews"));
        verify(reviewRepository).countByStatus("pending");
    }

    /** 荣誉分布：类别与级别两个维度均有数据，且映射为 {key, count} */
    @Test
    @SuppressWarnings("unchecked")
    void honors_returnsBothDimensions() {
        when(honorRepository.countGroupByCategory()).thenReturn(List.of(
                keyCount("scholarship", 12L), keyCount("competition", 8L)));
        when(honorRepository.countGroupByLevel()).thenReturn(List.of(
                keyCount("national", 6L), keyCount("school", 14L)));

        Map<String, Object> data = service.honors();

        List<Map<String, Object>> byCategory = (List<Map<String, Object>>) data.get("by_category");
        List<Map<String, Object>> byLevel = (List<Map<String, Object>>) data.get("by_level");
        assertEquals(2, byCategory.size(), "类别维度条数");
        assertEquals(2, byLevel.size(), "级别维度条数");
        assertEquals("scholarship", byCategory.get(0).get("key"), "首个类别 key");
        assertEquals(12L, byCategory.get(0).get("count"), "首个类别 count");
        assertEquals("national", byLevel.get(0).get("key"), "首个级别 key");
        assertEquals(6L, byLevel.get(0).get("count"), "首个级别 count");
    }

    /** 荣誉分布：空表时两个维度均为空列表而非 null */
    @Test
    @SuppressWarnings("unchecked")
    void honors_emptyTables_returnEmptyLists() {
        when(honorRepository.countGroupByCategory()).thenReturn(List.of());
        when(honorRepository.countGroupByLevel()).thenReturn(List.of());

        Map<String, Object> data = service.honors();

        assertTrue(((List<Map<String, Object>>) data.get("by_category")).isEmpty());
        assertTrue(((List<Map<String, Object>>) data.get("by_level")).isEmpty());
    }

    /** limit 未传（null）时使用默认值 8 */
    @Test
    void colleges_nullLimit_usesDefault8() {
        service.colleges(null);

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(collegeRankRepository).findCollegeRanks(captor.capture());
        assertEquals(StatsService.DEFAULT_LIMIT, captor.getValue(), "默认 limit 应为 8");
    }

    /** limit 边界值 1 合法并透传 */
    @Test
    void colleges_limit1_accepted() {
        service.colleges(1);

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(collegeRankRepository).findCollegeRanks(captor.capture());
        assertEquals(1, captor.getValue(), "limit=1 透传");
    }

    /** limit 边界值 100 合法并透传 */
    @Test
    void colleges_limit100_accepted() {
        service.colleges(100);

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(collegeRankRepository).findCollegeRanks(captor.capture());
        assertEquals(100, captor.getValue(), "limit=100 透传");
    }

    /** limit=0 → 400 INVALID_PARAMETER，且不落 Repository */
    @Test
    void colleges_limit0_throwsInvalidParameter() {
        BizException e = assertThrows(BizException.class, () -> service.colleges(0));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode(), "错误码");
        assertEquals(400, e.getErrorCode().httpStatus().value(), "HTTP 状态码应为 400");
        verifyNoInteractions(collegeRankRepository);
    }

    /** limit=101 → 400 INVALID_PARAMETER，且不落 Repository */
    @Test
    void colleges_limit101_throwsInvalidParameter() {
        BizException e = assertThrows(BizException.class, () -> service.colleges(101));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode(), "错误码");
        assertEquals(400, e.getErrorCode().httpStatus().value(), "HTTP 状态码应为 400");
        verifyNoInteractions(collegeRankRepository);
    }

    /** limit 为负数 → 400 INVALID_PARAMETER，且不落 Repository */
    @Test
    void colleges_negativeLimit_throwsInvalidParameter() {
        BizException e = assertThrows(BizException.class, () -> service.colleges(-5));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode(), "错误码");
        assertEquals(400, e.getErrorCode().httpStatus().value(), "HTTP 状态码应为 400");
        verifyNoInteractions(collegeRankRepository);
    }

    /** 学院排行：Repository 结果按序透传（降序由 SQL 保证） */
    @Test
    void colleges_returnsRepositoryRowsInOrder() {
        when(collegeRankRepository.findCollegeRanks(8)).thenReturn(List.of(
                collegeRank(1L, "计算机学院", 42L),
                collegeRank(3L, "经济管理学院", 30L)));

        List<CollegeRankProjection> ranks = service.colleges(null);

        assertEquals(2, ranks.size());
        assertEquals(1L, ranks.get(0).getCollegeId());
        assertEquals("计算机学院", ranks.get(0).getCollegeName());
        assertEquals(42L, ranks.get(0).getArchiveCount());
        assertEquals("经济管理学院", ranks.get(1).getCollegeName());
        verify(collegeRankRepository).findCollegeRanks(8);
    }

    // ------------------------------------------------------------------
    // [M03] 荣誉四维分布
    // ------------------------------------------------------------------

    /** dimension 非法 → 400 INVALID_PARAMETER，且不落 Repository */
    @Test
    void honorDistribution_invalidDimension_throwsInvalidParameter() {
        BizException e = assertThrows(BizException.class,
                () -> service.honorDistribution("student", null, null, null));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode(), "错误码");
        assertEquals(400, e.getErrorCode().httpStatus().value(), "HTTP 状态码应为 400");
        verifyNoInteractions(honorDistributionRepository);
    }

    /** dimension 缺省按 category，limit 缺省 20，结果组装为 {dimension, college_id, year, list} */
    @Test
    @SuppressWarnings("unchecked")
    void honorDistribution_nullDimension_defaultsCategory() {
        when(honorDistributionRepository.byCategory(null, null, 20)).thenReturn(List.of(
                keyCount("scholarship", 12L)));

        Map<String, Object> data = service.honorDistribution(null, null, null, null);

        assertEquals("category", data.get("dimension"), "缺省维度应为 category");
        assertNull(data.get("college_id"));
        assertNull(data.get("year"));
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertEquals(1, list.size());
        assertEquals("scholarship", list.get(0).get("key"));
        assertEquals(12L, list.get(0).get("count"));
        verify(honorDistributionRepository).byCategory(null, null, 20);
    }

    /** dimension=college + 过滤条件（college_id/year）与 limit 透传 */
    @Test
    void honorDistribution_collegeDimension_passesFilters() {
        when(honorDistributionRepository.byCollege(3L, 2025, 10)).thenReturn(List.of());

        service.honorDistribution("college", 3L, 2025, 10);

        verify(honorDistributionRepository).byCollege(3L, 2025, 10);
    }

    /** dimension=year / level 分别路由到对应方法 */
    @Test
    void honorDistribution_yearAndLevel_dispatchCorrectly() {
        when(honorDistributionRepository.byYear(null, null, 20)).thenReturn(List.of());
        when(honorDistributionRepository.byLevel(null, null, 20)).thenReturn(List.of());

        service.honorDistribution("year", null, null, null);
        service.honorDistribution("level", null, null, null);

        verify(honorDistributionRepository).byYear(null, null, 20);
        verify(honorDistributionRepository).byLevel(null, null, 20);
    }

    /** limit=0 → 400 INVALID_PARAMETER */
    @Test
    void honorDistribution_limit0_throwsInvalidParameter() {
        BizException e = assertThrows(BizException.class,
                () -> service.honorDistribution("college", null, null, 0));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        verifyNoInteractions(honorDistributionRepository);
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private KeyCountProjection keyCount(String k, Long cnt) {
        return new KeyCountProjection() {
            @Override
            public String getK() {
                return k;
            }

            @Override
            public Long getCnt() {
                return cnt;
            }
        };
    }

    private CollegeRankProjection collegeRank(Long collegeId, String collegeName, Long archiveCount) {
        return new CollegeRankProjection() {
            @Override
            public Long getCollegeId() {
                return collegeId;
            }

            @Override
            public String getCollegeName() {
                return collegeName;
            }

            @Override
            public Long getArchiveCount() {
                return archiveCount;
            }
        };
    }
}
