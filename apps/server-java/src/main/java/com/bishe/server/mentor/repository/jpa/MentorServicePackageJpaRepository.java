package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.MentorServicePackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 导师服务套餐 JPA 仓储。
 */
public interface MentorServicePackageJpaRepository extends JpaRepository<MentorServicePackageEntity, Long> {

    List<MentorServicePackageEntity> findByMentorUserIdOrderBySortNoAscIdAsc(long mentorUserId);

    List<MentorServicePackageEntity> findByMentorUserIdAndEnabledTrueOrderBySortNoAscIdAsc(long mentorUserId);

    List<MentorServicePackageEntity> findByMentorUserIdInOrderByMentorUserIdAscSortNoAscIdAsc(Collection<Long> mentorUserIds);

    Optional<MentorServicePackageEntity> findByMentorUserIdAndIdAndEnabledTrue(long mentorUserId, long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MentorServicePackageEntity entity where entity.mentorUserId = :mentorUserId")
    void deleteAllByMentorUserId(@Param("mentorUserId") long mentorUserId);
}
