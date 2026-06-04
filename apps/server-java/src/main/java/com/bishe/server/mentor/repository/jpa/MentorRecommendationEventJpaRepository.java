package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.MentorRecommendationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface MentorRecommendationEventJpaRepository extends JpaRepository<MentorRecommendationEventEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MentorRecommendationEventEntity entity
             where entity.runId in :runIds
            """)
    int deleteByRunIdIn(@Param("runIds") Collection<Long> runIds);
}
