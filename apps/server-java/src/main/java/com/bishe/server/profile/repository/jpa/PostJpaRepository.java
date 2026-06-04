package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/**
 * 社区帖子计数 JPA 仓储。
 */
public interface PostJpaRepository extends JpaRepository<PostEntity, Long> {

    long countByUserIdAndDeletedFalseAndModerationStatusAndCreatedAtGreaterThanEqual(
            long userId,
            String moderationStatus,
            Instant createdAt
    );

    boolean existsByUserIdAndDeletedFalseAndCreatedAtBetween(long userId, Instant startAt, Instant endAt);
}
