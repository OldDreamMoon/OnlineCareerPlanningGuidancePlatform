package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.InterviewMessageRefEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * 面试消息计数 JPA 仓储。
 */
public interface InterviewMessageRefJpaRepository extends JpaRepository<InterviewMessageRefEntity, Long> {

    @Query("""
            select count(message)
              from InterviewMessageRefEntity message
              join message.session session
             where session.studentUserId = :studentUserId
               and session.userDeletedAt is null
               and message.senderRole = :senderRole
               and message.createdAt >= :windowStart
            """)
    long countByStudentUserIdAndSenderRoleSince(
            @Param("studentUserId") long studentUserId,
            @Param("senderRole") String senderRole,
            @Param("windowStart") Instant windowStart
    );
}
