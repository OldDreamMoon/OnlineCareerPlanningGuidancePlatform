package com.bishe.server.ai.interview.jpa;

import com.bishe.server.ai.interview.jpa.entity.InterviewMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文本面试消息 JPA 仓储。
 */
public interface InterviewMessageJpaRepository extends JpaRepository<InterviewMessageEntity, Long> {

    List<InterviewMessageEntity> findBySessionPkOrderByIdAsc(long sessionPk);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from InterviewMessageEntity message
             where message.sessionPk = :sessionPk
            """)
    int deleteBySessionPk(@Param("sessionPk") long sessionPk);
}
