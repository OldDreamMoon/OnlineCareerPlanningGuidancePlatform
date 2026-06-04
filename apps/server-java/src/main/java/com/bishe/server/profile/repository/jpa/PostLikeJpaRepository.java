package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.PostLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * 帖子点赞计数 JPA 仓储。
 */
public interface PostLikeJpaRepository extends JpaRepository<PostLikeEntity, Long> {

    @Query("""
            select count(postLike)
              from PostLikeEntity postLike
              join postLike.post post
             where post.userId = :userId
               and post.deleted = false
               and post.moderationStatus = :moderationStatus
               and postLike.createdAt >= :windowStart
            """)
    long countLikesReceivedByPostOwnerSince(
            @Param("userId") long userId,
            @Param("moderationStatus") String moderationStatus,
            @Param("windowStart") Instant windowStart
    );
}
