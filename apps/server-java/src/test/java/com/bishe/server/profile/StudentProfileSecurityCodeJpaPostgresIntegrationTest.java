package com.bishe.server.profile;

import com.bishe.server.profile.repository.StudentProfileSecurityCodeRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 学生资料安全验证码仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(StudentProfileSecurityCodeRepository.class)
class StudentProfileSecurityCodeJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long SEEDED_STUDENT_USER_ID = 1L;
    private static final String PURPOSE = "PROFILE_EMAIL_CURRENT_CODE";
    private static final String TARGET_EMAIL = "skill-pg-student@example.com";

    @Autowired
    private StudentProfileSecurityCodeRepository studentProfileSecurityCodeRepository;

    @Test
    void studentProfileSecurityCodeRepositoryShouldSupportUpsertAndDeleteOnPostgres() {
        String firstCodeHash = "a".repeat(64);
        Instant firstExpiresAt = Instant.parse("2026-04-16T08:10:00Z");
        Instant firstNextSendAt = Instant.parse("2026-04-16T08:01:00Z");

        assertThat(studentProfileSecurityCodeRepository.findByIdentity(SEEDED_STUDENT_USER_ID, PURPOSE, TARGET_EMAIL)).isEmpty();

        studentProfileSecurityCodeRepository.saveOrUpdate(
                SEEDED_STUDENT_USER_ID,
                PURPOSE,
                TARGET_EMAIL,
                firstCodeHash,
                firstExpiresAt,
                firstNextSendAt
        );

        assertThat(studentProfileSecurityCodeRepository.findByIdentity(SEEDED_STUDENT_USER_ID, PURPOSE, TARGET_EMAIL))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.userId()).isEqualTo(SEEDED_STUDENT_USER_ID);
                    assertThat(record.purpose()).isEqualTo(PURPOSE);
                    assertThat(record.targetEmail()).isEqualTo(TARGET_EMAIL);
                    assertThat(record.codeHash()).isEqualTo(firstCodeHash);
                    assertThat(record.expiresAt()).isEqualTo(firstExpiresAt);
                    assertThat(record.nextSendAt()).isEqualTo(firstNextSendAt);
                });

        String secondCodeHash = "b".repeat(64);
        Instant secondExpiresAt = Instant.parse("2026-04-16T08:20:00Z");
        Instant secondNextSendAt = Instant.parse("2026-04-16T08:02:00Z");
        studentProfileSecurityCodeRepository.saveOrUpdate(
                SEEDED_STUDENT_USER_ID,
                PURPOSE,
                TARGET_EMAIL,
                secondCodeHash,
                secondExpiresAt,
                secondNextSendAt
        );

        assertThat(studentProfileSecurityCodeRepository.findByIdentity(SEEDED_STUDENT_USER_ID, PURPOSE, TARGET_EMAIL))
                .isPresent()
                .get()
                .satisfies(record -> {
                    assertThat(record.codeHash()).isEqualTo(secondCodeHash);
                    assertThat(record.expiresAt()).isEqualTo(secondExpiresAt);
                    assertThat(record.nextSendAt()).isEqualTo(secondNextSendAt);
                });

        studentProfileSecurityCodeRepository.deleteByIdentity(SEEDED_STUDENT_USER_ID, PURPOSE, TARGET_EMAIL);
        assertThat(studentProfileSecurityCodeRepository.findByIdentity(SEEDED_STUDENT_USER_ID, PURPOSE, TARGET_EMAIL)).isEmpty();
    }
}
