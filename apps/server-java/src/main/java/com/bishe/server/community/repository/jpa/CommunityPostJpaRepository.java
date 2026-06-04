package com.bishe.server.community.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CommunityPostJpaRepository extends JpaRepository<PostEntity, Long>, JpaSpecificationExecutor<PostEntity> {

    interface LeaderboardPostStatView {
        Long getUserId();

        long getPostCount();

        Instant getLastPostAt();
    }

    Optional<PostEntity> findByIdAndDeletedFalse(Long id);

    boolean existsByIdAndDeletedFalseAndModerationStatus(Long id, String moderationStatus);

    @Query("""
            select p
              from PostEntity p
              join p.author author
             where p.id = :postId
               and p.deleted = false
               and author.deleted = false
            """)
    Optional<PostEntity> findReadableById(@Param("postId") long postId);

    @Query("""
            select p.userId
              from PostEntity p
             where p.id = :postId
               and p.deleted = false
            """)
    Optional<Long> findAuthorUserIdByIdAndDeletedFalse(@Param("postId") long postId);

    @Query("""
            select p.id
              from PostEntity p
             where p.userId = :userId
               and p.deleted = false
               and p.moderationStatus = :moderationStatus
            """)
    List<Long> findIdsByUserIdAndDeletedFalseAndModerationStatus(
            @Param("userId") long userId,
            @Param("moderationStatus") String moderationStatus
    );

    @Query("""
            select p.userId as userId,
                   count(p) as postCount,
                   max(p.createdAt) as lastPostAt
              from PostEntity p
             where p.deleted = false
               and p.moderationStatus = :moderationStatus
               and p.createdAt >= :windowStart
          group by p.userId
            """)
    List<LeaderboardPostStatView> summarizeVisiblePostsSince(
            @Param("moderationStatus") String moderationStatus,
            @Param("windowStart") Instant windowStart
    );
}
