package com.bishe.server.governance.jpa;

import com.bishe.server.governance.jpa.entity.ContentModerationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ContentModerationEventJpaRepository extends JpaRepository<ContentModerationEventEntity, Long> {

    interface ModerationSummaryView {
        long getTotalCount();

        long getBlockedCount();
    }

    @Query("""
            select count(event) as totalCount,
                   coalesce(sum(case when event.action = 'BLOCK' then 1 else 0 end), 0) as blockedCount
              from ContentModerationEventEntity event
             where event.createdAt >= :startAt
               and event.createdAt <= :endAt
            """)
    ModerationSummaryView summarizeModeration(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);
}
