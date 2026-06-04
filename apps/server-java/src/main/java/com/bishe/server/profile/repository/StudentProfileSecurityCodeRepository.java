package com.bishe.server.profile.repository;

import com.bishe.server.profile.repository.jpa.StudentProfileSecurityCodeJpaRepository;
import com.bishe.server.profile.repository.jpa.entity.StudentProfileSecurityCodeEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 学生资料中心安全验证码仓储。
 */
@Repository
public class StudentProfileSecurityCodeRepository {

    private final StudentProfileSecurityCodeJpaRepository studentProfileSecurityCodeJpaRepository;

    public StudentProfileSecurityCodeRepository(StudentProfileSecurityCodeJpaRepository studentProfileSecurityCodeJpaRepository) {
        this.studentProfileSecurityCodeJpaRepository = studentProfileSecurityCodeJpaRepository;
    }

    public Optional<SecurityCodeRow> findByIdentity(long userId, String purpose, String targetEmail) {
        return studentProfileSecurityCodeJpaRepository
                .findByStudentUserIdAndPurposeAndTargetEmail(userId, purpose, targetEmail)
                .map(this::toRow);
    }

    public void saveOrUpdate(long userId, String purpose, String targetEmail, String codeHash, Instant expiresAt, Instant nextSendAt) {
        StudentProfileSecurityCodeEntity entity = studentProfileSecurityCodeJpaRepository
                .findByStudentUserIdAndPurposeAndTargetEmail(userId, purpose, targetEmail)
                .orElseGet(() -> StudentProfileSecurityCodeEntity.create(userId, purpose, targetEmail));
        entity.setCodeHash(codeHash);
        entity.setExpiresAt(expiresAt);
        entity.setNextSendAt(nextSendAt);
        studentProfileSecurityCodeJpaRepository.save(entity);
    }

    public void deleteByIdentity(long userId, String purpose, String targetEmail) {
        studentProfileSecurityCodeJpaRepository.findByStudentUserIdAndPurposeAndTargetEmail(userId, purpose, targetEmail)
                .ifPresent(studentProfileSecurityCodeJpaRepository::delete);
    }

    private SecurityCodeRow toRow(StudentProfileSecurityCodeEntity entity) {
        return new SecurityCodeRow(
                entity.getStudentUserId(),
                entity.getPurpose(),
                entity.getTargetEmail(),
                entity.getCodeHash(),
                entity.getExpiresAt(),
                entity.getNextSendAt()
        );
    }

    public record SecurityCodeRow(
            long userId,
            String purpose,
            String targetEmail,
            String codeHash,
            Instant expiresAt,
            Instant nextSendAt
    ) {
    }
}
