package com.bishe.server.auth.repository.jpa;

import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户账号 JPA 仓储。
 */
public interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, Long>, JpaSpecificationExecutor<UserAccountEntity> {

    Optional<UserAccountEntity> findByEmailAndDeletedFalse(String email);

    Optional<UserAccountEntity> findByIdAndDeletedFalse(Long id);

    Optional<UserAccountEntity> findByIdAndRoleAndDeletedFalse(Long id, UserRole role);

    boolean existsByIdAndRoleAndDeletedFalse(Long id, UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u.id
              from UserAccountEntity u
             where u.id = :id
               and u.role = :role
               and u.deleted = false
            """)
    Optional<Long> lockIdByIdAndRoleAndDeletedFalse(@Param("id") long id, @Param("role") UserRole role);

    @Query("""
            select u.id
              from UserAccountEntity u
             where u.deleted = false
               and u.status = :status
          order by u.id asc
            """)
    List<Long> findIdsByStatusAndDeletedFalseOrderByIdAsc(@Param("status") UserAccountStatus status);

    @Query("""
            select u.id
              from UserAccountEntity u
             where u.deleted = false
               and u.status = :status
               and u.role in :roles
          order by u.id asc
            """)
    List<Long> findIdsByStatusAndRoleInAndDeletedFalseOrderByIdAsc(
            @Param("status") UserAccountStatus status,
            @Param("roles") Collection<UserRole> roles
    );

    @Query("""
            select u.id
              from UserAccountEntity u
             where u.deleted = false
               and u.role = :role
          order by u.id asc
            """)
    List<Long> findIdsByRoleAndDeletedFalseOrderByIdAsc(@Param("role") UserRole role);

    @Query("""
            select u.id
              from UserAccountEntity u
             where u.deleted = false
               and lower(u.displayName) like concat('%', :keyword, '%')
          order by u.id asc
            """)
    List<Long> findIdsByDisplayNameContainingIgnoreCaseAndDeletedFalse(@Param("keyword") String keyword);
}
