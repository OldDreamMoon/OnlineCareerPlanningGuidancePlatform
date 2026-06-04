package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.StudentPortraitSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 学生画像快照 JPA 仓储。
 */
public interface StudentPortraitSnapshotJpaRepository extends JpaRepository<StudentPortraitSnapshotEntity, Long> {

    Optional<StudentPortraitSnapshotEntity> findByStudentUserId(long studentUserId);
}
