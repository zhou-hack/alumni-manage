package com.alumni.notifications.repository;

import com.alumni.notifications.entity.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    List<NotificationSubscription> findByUserId(Long userId);

    Optional<NotificationSubscription> findByUserIdAndChannel(Long userId, String channel);
}
