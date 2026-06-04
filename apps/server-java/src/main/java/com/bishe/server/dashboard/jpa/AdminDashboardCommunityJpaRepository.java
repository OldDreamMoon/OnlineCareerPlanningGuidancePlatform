package com.bishe.server.dashboard.jpa;

import com.bishe.server.profile.repository.jpa.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AdminDashboardCommunityJpaRepository extends JpaRepository<PostEntity, Long> {

    @Query("""
            select count(p)
              from PostEntity p
             where p.deleted = false
               and p.moderationStatus = 'PASS'
               and p.createdAt >= :startAt
               and p.createdAt <= :endAt
            """)
    long countVisiblePosts(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Query("""
            select count(p)
              from PostEntity p
             where p.deleted = false
               and p.moderationStatus = 'PASS'
               and p.createdAt >= :startAt
               and p.createdAt <= :endAt
               and exists (
                    select c.id
                      from CommentEntity c
                     where c.postId = p.id
                       and c.deleted = false
                       and c.moderationStatus = 'PASS'
                       and c.ai = true
               )
            """)
    long countAiCoveredPosts(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);
}
