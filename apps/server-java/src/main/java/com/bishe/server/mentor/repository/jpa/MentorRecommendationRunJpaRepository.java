package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.MentorRecommendationRunEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MentorRecommendationRunJpaRepository extends JpaRepository<MentorRecommendationRunEntity, Long> {

    @Query("""
            select entity.id
              from MentorRecommendationRunEntity entity
             where entity.createdAt < :beforeTime
          order by entity.createdAt asc, entity.id asc
            """)
    List<Long> findRunIdsBefore(@Param("beforeTime") Instant beforeTime, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MentorRecommendationRunEntity entity
             where entity.id in :runIds
            """)
    int deleteByIdIn(@Param("runIds") Collection<Long> runIds);
}
