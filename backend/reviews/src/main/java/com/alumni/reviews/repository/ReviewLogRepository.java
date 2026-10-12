package com.alumni.reviews.repository;

import com.alumni.reviews.entity.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    List<ReviewLog> findByReviewIdOrderByCreatedAtAscIdAsc(Long reviewId);
}
