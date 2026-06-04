package com.bishe.server.profile.repository.jpa;

import com.bishe.server.profile.repository.jpa.entity.StudentProfilePrivacySettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 学生隐私设置 JPA 仓储。
 */
public interface StudentProfilePrivacySettingsJpaRepository extends JpaRepository<StudentProfilePrivacySettingsEntity, Long> {

    Optional<StudentProfilePrivacySettingsEntity> findByStudentUserId(long studentUserId);
}
