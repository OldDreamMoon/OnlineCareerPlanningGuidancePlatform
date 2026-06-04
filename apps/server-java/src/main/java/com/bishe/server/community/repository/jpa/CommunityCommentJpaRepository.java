package com.bishe.server.community.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CommunityCommentJpaRepository extends JpaRepository<CommentEntity, Long> {

    interface PostCommentStatsView {
        Long getPostId();

        long getCommentCount();

        long getMentorReplyCount();
    }

    interface LeaderboardCommentStatView {
        Long getUserId();

        long getCommentCount();

        Instant getLastCommentAt();
    }

    @Query("""
            select c
              from CommentEntity c
              join c.author author
             where c.postId = :postId
               and c.deleted = false
               and c.moderationStatus = :moderationStatus
               and author.deleted = false
          order by c.createdAt asc, c.id asc
            """)
    List<CommentEntity> findVisibleComments(
            @Param("postId") long postId,
            @Param("moderationStatus") String moderationStatus
    );

    @Query("""
            select c
              from CommentEntity c
              join c.author author
             where c.postId = :postId
               and c.deleted = false
               and author.deleted = false
          order by c.createdAt asc, c.id asc
            """)
    List<CommentEntity> findAdminReadableComments(@Param("postId") long postId);

    @Query("""
            select c.postId as postId,
                   count(c) as commentCount,
                   coalesce(sum(case when author.role = com.bishe.server.auth.model.UserRole.MENTOR and c.ai = false then 1 else 0 end), 0) as mentorReplyCount
              from CommentEntity c
              join c.author author
             where c.deleted = false
               and c.moderationStatus = :moderationStatus
               and author.deleted = false
               and c.postId in :postIds
          group by c.postId
            """)
    List<PostCommentStatsView> summarizeVisibleCommentsByPostIds(
            @Param("postIds") Collection<Long> postIds,
            @Param("moderationStatus") String moderationStatus
    );

    @Query("""
            select distinct c.postId
              from CommentEntity c
             where c.userId = :userId
               and c.deleted = false
               and c.moderationStatus = :moderationStatus
               and c.postId in :postIds
            """)
    List<Long> findParticipatedPostIds(
            @Param("userId") long userId,
            @Param("postIds") Collection<Long> postIds,
            @Param("moderationStatus") String moderationStatus
    );

    @Query("""
            select distinct c.postId
              from CommentEntity c
             where c.userId = :userId
               and c.deleted = false
               and c.moderationStatus = :commentModerationStatus
               and exists (
                    select 1
                      from com.bishe.server.profile.repository.jpa.entity.PostEntity p
                     where p.id = c.postId
                       and p.deleted = false
                       and p.moderationStatus = :postModerationStatus
               )
            """)
    List<Long> findVisibleCommentedPostIdsByUserId(
            @Param("userId") long userId,
            @Param("commentModerationStatus") String commentModerationStatus,
            @Param("postModerationStatus") String postModerationStatus
    );

    @Query("""
            select c.userId as userId,
                   count(c) as commentCount,
                   max(c.createdAt) as lastCommentAt
              from CommentEntity c
             where c.deleted = false
               and c.moderationStatus = :moderationStatus
               and c.createdAt >= :windowStart
          group by c.userId
            """)
    List<LeaderboardCommentStatView> summarizeVisibleCommentsSince(
            @Param("moderationStatus") String moderationStatus,
            @Param("windowStart") Instant windowStart
    );
}
