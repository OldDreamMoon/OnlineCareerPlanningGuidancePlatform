package com.bishe.server.auth.repository.jpa;

import com.bishe.server.auth.repository.jpa.entity.MentorApprovalProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 导师认证状态 JPA 仓储。
 */
public interface MentorApprovalProfileJpaRepository extends JpaRepository<MentorApprovalProfileEntity, Long> {

    Optional<MentorApprovalProfileEntity> findByUserId(long userId);

    @Query("""
            select profile.approvalStatus
              from MentorApprovalProfileEntity profile
             where profile.userId = :userId
            """)
    Optional<String> findApprovalStatusByUserId(@Param("userId") long userId);

    @Query("""
            select profile.userId
              from MentorApprovalProfileEntity profile
             where profile.approvalStatus = :approvalStatus
            """)
    List<Long> findUserIdsByApprovalStatus(@Param("approvalStatus") String approvalStatus);
}
