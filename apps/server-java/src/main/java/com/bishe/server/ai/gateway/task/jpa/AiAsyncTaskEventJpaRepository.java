package com.bishe.server.ai.gateway.task.jpa;

import com.bishe.server.ai.gateway.task.jpa.entity.AiAsyncTaskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * AI 异步任务事件 JPA 仓储。
 */
public interface AiAsyncTaskEventJpaRepository extends JpaRepository<AiAsyncTaskEventEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AiAsyncTaskEventEntity entity
             where entity.taskJobId in :jobIds
            """)
    int deleteByTaskJobIdIn(@Param("jobIds") Collection<Long> jobIds);
}
