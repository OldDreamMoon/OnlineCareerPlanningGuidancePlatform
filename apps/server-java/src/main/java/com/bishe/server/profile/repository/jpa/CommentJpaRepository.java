package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/**
 * 社区评论计数 JPA 仓储。
 */
public interface CommentJpaRepository extends JpaRepository<CommentEntity, Long> {

    long countByUserIdAndDeletedFalseAndModerationStatusAndCreatedAtGreaterThanEqual(
            long userId,
            String moderationStatus,
            Instant createdAt
    );

    boolean existsByUserIdAndDeletedFalseAndCreatedAtBetween(long userId, Instant startAt, Instant endAt);
}
