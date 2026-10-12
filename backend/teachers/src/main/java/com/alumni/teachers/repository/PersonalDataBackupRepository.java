package com.alumni.teachers.repository;

import com.alumni.teachers.entity.PersonalDataBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PersonalDataBackupRepository extends JpaRepository<PersonalDataBackup, Long> {

    /** 7 天滚动清理：删除早于保留线的快照（同步成功后同事务执行） */
    @Modifying
    @Query("DELETE FROM PersonalDataBackup b WHERE b.snapshotDate < :retainFrom")
    int deleteOlderThan(@Param("retainFrom") LocalDate retainFrom);
}
