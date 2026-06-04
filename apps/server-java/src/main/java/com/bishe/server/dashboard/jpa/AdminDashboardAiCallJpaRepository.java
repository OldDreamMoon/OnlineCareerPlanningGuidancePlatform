package com.bishe.server.dashboard.jpa;

import com.bishe.server.ai.quota.jpa.entity.AiCallLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AdminDashboardAiCallJpaRepository extends JpaRepository<AiCallLogEntity, Long> {

    interface UserFirstSuccessView {
        Long getUserId();

        Instant getFirstSuccessAt();
    }

    interface AiCallSummaryView {
        long getTotalCount();

        long getSuccessCount();
    }

    @Query("""
            select log.userId as userId,
                   min(log.createdAt) as firstSuccessAt
              from AiCallLogEntity log
             where log.status = 'SUCCESS'
               and log.userId in :userIds
          group by log.userId
            """)
    List<UserFirstSuccessView> findFirstSuccessfulAiCallAt(@Param("userIds") Collection<Long> userIds);

    @Query("""
            select count(log) as totalCount,
                   coalesce(sum(case when log.status = 'SUCCESS' then 1 else 0 end), 0) as successCount
              from AiCallLogEntity log
             where log.createdAt >= :startAt
               and log.createdAt <= :endAt
            """)
    AiCallSummaryView summarizeAiCalls(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);
}
