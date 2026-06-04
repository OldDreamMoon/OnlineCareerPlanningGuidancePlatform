package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.MentorRecommendationSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MentorRecommendationSnapshotJpaRepository extends JpaRepository<MentorRecommendationSnapshotEntity, Long> {

    Optional<MentorRecommendationSnapshotEntity> findByMentorUserId(long mentorUserId);
}
