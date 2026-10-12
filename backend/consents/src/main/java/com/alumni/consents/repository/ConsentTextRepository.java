package com.alumni.consents.repository;

import com.alumni.consents.entity.ConsentText;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsentTextRepository extends JpaRepository<ConsentText, Long> {

    /** 当前生效授权文本：version 最大的一条 */
    Optional<ConsentText> findTopByOrderByVersionDesc();
}
