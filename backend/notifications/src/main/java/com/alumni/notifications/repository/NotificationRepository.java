package com.alumni.notifications.repository;

import com.alumni.notifications.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 我的消息（created_at 倒序，id 倒序兜底保证稳定） */
    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDescIdDesc(Long userId, String type, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndTypeAndReadFalseOrderByCreatedAtDescIdDesc(Long userId, String type,
                                                                                 Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    /** 未读数按类型聚合（仅返回有未读的类型） */
    @Query("select n.type, count(n) from Notification n where n.userId = :userId and n.read = false group by n.type")
    List<Object[]> countUnreadGroupByType(@Param("userId") Long userId);

    /** 全部已读（可限定 type），返回更新行数 */
    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllReadByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.type = :type and n.read = false")
    int markAllReadByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);
}
