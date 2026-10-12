package com.alumni.wall.repository;

import com.alumni.wall.entity.WallMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WallMessageRepository extends JpaRepository<WallMessage, Long> {

    /**
     * 公开列表（仅 approved）：姓名/届次/学院经 users ⋈ alumni_base 联查，
     * 置顶优先、时间倒序（id 兜底稳定排序）。
     */
    @Query(value = """
            SELECT m.id, m.user_id, ab.real_name AS name, ab.grad_year, ab.college,
                   m.content, m.show_name, m.is_pinned, m.created_at
            FROM wall_message m
            JOIN users u ON u.id = m.user_id
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE m.status = 'approved'
            ORDER BY m.is_pinned DESC, m.created_at DESC, m.id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<PublicMessageProjection> findApproved(@Param("limit") int limit, @Param("offset") int offset);

    /** 公开列表总数（COUNT 与明细两条 SQL） */
    @Query(value = "SELECT COUNT(*) FROM wall_message WHERE status = 'approved'", nativeQuery = true)
    long countApproved();

    /** 我的留言（全部状态，含 pending/rejected 及驳回原因） */
    List<WallMessage> findByUserIdOrderByIdDesc(Long userId);

    /** 公开列表行投影（snake_case 列名 ↔ camelCase getter，与 stats/showcase 同模式） */
    interface PublicMessageProjection {
        Long getId();

        Long getUserId();

        String getName();

        Integer getGradYear();

        String getCollege();

        String getContent();

        Boolean getShowName();

        Boolean getIsPinned();

        java.sql.Timestamp getCreatedAt();
    }
}
