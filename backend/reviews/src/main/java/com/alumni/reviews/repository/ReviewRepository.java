package com.alumni.reviews.repository;

import com.alumni.reviews.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByStageAndStatus(String stage, String status, Pageable pageable);

    long countByStageAndStatus(String stage, String status);

    Page<Review> findByStageAndStatusAndBizType(String stage, String status, String bizType, Pageable pageable);

    long countByStageAndStatusAndBizType(String stage, String status, String bizType);
}
