package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.MentorFavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 导师收藏关系 JPA 仓储。
 */
public interface MentorFavoriteJpaRepository extends JpaRepository<MentorFavoriteEntity, Long> {

    boolean existsByStudentUserIdAndMentorUserId(long studentUserId, long mentorUserId);

    long countByStudentUserId(long studentUserId);

    @Query("""
            SELECT favorite.mentorUserId
              FROM MentorFavoriteEntity favorite
             WHERE favorite.studentUserId = :studentUserId
          ORDER BY favorite.createdAt DESC, favorite.id DESC
            """)
    List<Long> findMentorUserIdsByStudentUserId(@Param("studentUserId") long studentUserId);

    @Query("""
            SELECT favorite.mentorUserId
              FROM MentorFavoriteEntity favorite
             WHERE favorite.studentUserId = :studentUserId
               AND favorite.mentorUserId IN :mentorUserIds
            """)
    List<Long> findMentorUserIdsByStudentUserIdAndMentorUserIdIn(
            @Param("studentUserId") long studentUserId,
            @Param("mentorUserIds") List<Long> mentorUserIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM MentorFavoriteEntity favorite
             WHERE favorite.studentUserId = :studentUserId
               AND favorite.mentorUserId = :mentorUserId
            """)
    int deleteByStudentUserIdAndMentorUserId(
            @Param("studentUserId") long studentUserId,
            @Param("mentorUserId") long mentorUserId
    );
}
