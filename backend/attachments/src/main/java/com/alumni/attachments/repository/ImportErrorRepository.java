package com.alumni.attachments.repository;

import com.alumni.attachments.entity.ImportError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {

    List<ImportError> findByImportIdOrderByRowNoAscIdAsc(Long importId);
}
