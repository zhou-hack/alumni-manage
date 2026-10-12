package com.alumni.showcase.repository;

import com.alumni.showcase.entity.Showcase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShowcaseRepository extends JpaRepository<Showcase, Long> {

    boolean existsByArchiveId(Long archiveId);

    long deleteByArchiveId(Long archiveId);

    /**
     * 大屏 feed 红线口径（单条 SQL 保证，不依赖应用层二次过滤）：
     * showcase ⋈ archives(status='approved') ⋈ consents(scope='screen')
     * —— 三项条件任一不满足，该行天然不出现在结果集中。
     */
    @Query(value = """
            SELECT a.id, a.title, a.category, u.nickname AS owner, ab.college, ab.grad_year, att.url AS cover_url
            FROM showcase s
            JOIN archives a ON a.id = s.archive_id AND a.status = 'approved'
            JOIN consents c ON c.archive_id = a.id AND c.scope = 'screen'
            JOIN users u ON u.id = a.user_id
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            LEFT JOIN attachments att ON att.id = s.cover_attachment_id
            ORDER BY s.published_at DESC, s.id DESC LIMIT 50
            """, nativeQuery = true)
    List<FeedProjection> feedAll();

    /** 学院筛选变体：alumni_base.college 文本对齐 dicts college.label */
    @Query(value = """
            SELECT a.id, a.title, a.category, u.nickname AS owner, ab.college, ab.grad_year, att.url AS cover_url
            FROM showcase s
            JOIN archives a ON a.id = s.archive_id AND a.status = 'approved'
            JOIN consents c ON c.archive_id = a.id AND c.scope = 'screen'
            JOIN users u ON u.id = a.user_id
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            LEFT JOIN attachments att ON att.id = s.cover_attachment_id
            WHERE ab.college = (SELECT label FROM dicts WHERE id = :collegeId AND type = 'college')
            ORDER BY s.published_at DESC, s.id DESC LIMIT 50
            """, nativeQuery = true)
    List<FeedProjection> feedByCollege(@Param("collegeId") Long collegeId);
}
