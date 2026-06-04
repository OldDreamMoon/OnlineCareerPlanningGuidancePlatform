package com.bishe.server.community.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.PostLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CommunityPostLikeJpaRepository extends JpaRepository<PostLikeEntity, Long> {

    interface PostLikeCountView {
        Long getPostId();

        long getLikeCount();
    }

    interface LeaderboardLikeStatView {
        Long getUserId();

        long getLikeReceivedCount();

        Instant getLastLikeAt();
    }

    boolean existsByPostIdAndUserId(long postId, long userId);

    long countByPostId(long postId);

    void deleteByPostIdAndUserId(long postId, long userId);

    @Query("""
            select postLike.postId
              from PostLikeEntity postLike
             where postLike.userId = :userId
               and postLike.postId in :postIds
            """)
    List<Long> findLikedPostIds(
            @Param("userId") long userId,
            @Param("postIds") Collection<Long> postIds
    );

    @Query("""
            select postLike.postId as postId,
                   count(postLike) as likeCount
              from PostLikeEntity postLike
             where postLike.postId in :postIds
          group by postLike.postId
            """)
    List<PostLikeCountView> countLikesByPostIds(@Param("postIds") Collection<Long> postIds);

    @Query("""
            select post.userId as userId,
                   count(postLike) as likeReceivedCount,
                   max(postLike.createdAt) as lastLikeAt
              from PostLikeEntity postLike
              join postLike.post post
             where post.deleted = false
               and post.moderationStatus = :moderationStatus
               and postLike.createdAt >= :windowStart
          group by post.userId
            """)
    List<LeaderboardLikeStatView> summarizeLikesReceivedSince(
            @Param("moderationStatus") String moderationStatus,
            @Param("windowStart") Instant windowStart
    );
}
