package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.StudentProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 学生资料 JPA 仓储。
 */
public interface StudentProfileJpaRepository extends JpaRepository<StudentProfileEntity, Long> {

    Optional<StudentProfileEntity> findByUserId(long userId);
}
