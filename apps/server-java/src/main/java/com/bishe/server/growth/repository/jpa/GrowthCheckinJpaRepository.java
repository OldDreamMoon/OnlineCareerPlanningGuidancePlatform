package com.bishe.server.growth.repository.jpa;

import com.bishe.server.growth.repository.jpa.entity.GrowthCheckinEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 签到记录 JPA 仓储。
 */
public interface GrowthCheckinJpaRepository extends JpaRepository<GrowthCheckinEntity, Long> {

    @Query(
            value = """
                    SELECT EXISTS(
                        SELECT 1
                          FROM checkins
                         WHERE student_user_id = :studentUserId
                           AND checkin_date = CAST(:checkinDate AS DATE)
                    )
                    """,
            nativeQuery = true
    )
    boolean existsCheckin(
            @Param("studentUserId") long studentUserId,
            @Param("checkinDate") String checkinDate
    );

    @Query(
            value = """
                    SELECT id, student_user_id, checkin_date, streak_count, points_earned, created_at
                      FROM checkins
                     WHERE student_user_id = :studentUserId
                  ORDER BY checkin_date DESC
                     LIMIT 1
                    """,
            nativeQuery = true
    )
    List<GrowthCheckinEntity> findLatestCheckin(@Param("studentUserId") long studentUserId);

    @Query(
            value = """
                    SELECT id, student_user_id, checkin_date, streak_count, points_earned, created_at
                      FROM checkins
                     WHERE student_user_id = :studentUserId
                       AND checkin_date BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)
                  ORDER BY checkin_date ASC
                    """,
            nativeQuery = true
    )
    List<GrowthCheckinEntity> findCheckinsBetween(
            @Param("studentUserId") long studentUserId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO checkins(student_user_id, checkin_date, streak_count, points_earned, created_at)
                    VALUES (:studentUserId, CAST(:checkinDate AS DATE), :streakCount, :pointsEarned, CURRENT_TIMESTAMP)
                    """,
            nativeQuery = true
    )
    void insertCheckin(
            @Param("studentUserId") long studentUserId,
            @Param("checkinDate") String checkinDate,
            @Param("streakCount") int streakCount,
            @Param("pointsEarned") int pointsEarned
    );
}
