package com.alumni.admin.repository;

import com.alumni.admin.entity.FieldPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FieldPermissionRepository extends JpaRepository<FieldPermission, Long> {

    List<FieldPermission> findAll();

    Optional<FieldPermission> findByRoleAndField(String role, String field);
}
