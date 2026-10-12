package com.alumni.stats.repository;

import com.alumni.stats.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 待审数 = COUNT(reviews WHERE status = 'pending') */
    long countByStatus(String status);
}
