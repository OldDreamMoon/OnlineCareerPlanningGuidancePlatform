package com.bishe.server.dashboard.jpa;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AdminDashboardUserJpaRepository extends JpaRepository<UserAccountEntity, Long> {

    interface StudentLifecycleView {
        Long getUserId();

        Instant getCreatedAt();

        Instant getLastLoginAt();
    }

    @Query("""
            select u.id as userId,
                   u.createdAt as createdAt,
                   u.lastLoginAt as lastLoginAt
              from UserAccountEntity u
             where u.role = :role
               and u.deleted = false
               and u.createdAt >= :startAt
               and u.createdAt <= :endAt
          order by u.id asc
            """)
    List<StudentLifecycleView> findStudentLifecycle(
            @Param("role") UserRole role,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select u.id
              from UserAccountEntity u
             where u.role = :role
               and u.deleted = false
               and (
                    (u.lastLoginAt is not null and u.lastLoginAt >= :startAt and u.lastLoginAt <= :endAt)
                    or (u.createdAt >= :startAt and u.createdAt <= :endAt)
               )
          order by u.id asc
            """)
    List<Long> findActiveStudentIds(
            @Param("role") UserRole role,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    long countByRoleAndDeletedFalse(UserRole role);

    @Query("""
            select count(u)
              from UserAccountEntity u
             where u.role = :role
               and u.deleted = false
               and (
                    (u.lastLoginAt is not null and u.lastLoginAt >= :startAt)
                    or u.createdAt >= :startAt
               )
            """)
    long countActiveStudentsSince(@Param("role") UserRole role, @Param("startAt") Instant startAt);
}
