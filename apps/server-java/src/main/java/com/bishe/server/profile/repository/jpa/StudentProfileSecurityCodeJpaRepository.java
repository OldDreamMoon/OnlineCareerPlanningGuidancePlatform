package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.StudentProfileSecurityCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 学生资料安全验证码 JPA 仓储。
 */
public interface StudentProfileSecurityCodeJpaRepository extends JpaRepository<StudentProfileSecurityCodeEntity, Long> {

    Optional<StudentProfileSecurityCodeEntity> findByStudentUserIdAndPurposeAndTargetEmail(
            long studentUserId,
            String purpose,
            String targetEmail
    );
}
