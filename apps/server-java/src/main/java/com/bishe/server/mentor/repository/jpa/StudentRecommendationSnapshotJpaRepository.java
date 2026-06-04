package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.StudentRecommendationSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRecommendationSnapshotJpaRepository extends JpaRepository<StudentRecommendationSnapshotEntity, Long> {

    Optional<StudentRecommendationSnapshotEntity> findByStudentUserId(long studentUserId);
}
