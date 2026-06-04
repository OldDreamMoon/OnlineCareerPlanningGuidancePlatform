package com.bishe.server.governance;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 治理域 PostgreSQL 基线最小验证：确保缺失表与默认策略值已可供旧 JDBC 仓储使用。
 */
@DataJpaTest
@Import({ContentGovernanceRepository.class, ModerationPolicyRepository.class, SensitiveTermRepository.class})
class ContentGovernanceBaselinePostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long REPORTER_USER_ID = 9101L;
    private static final long OPERATOR_USER_ID = 9102L;
    private static final String TEST_TERM = "PG_GOVERNANCE_TERM";
    private static final String TEST_POLICY_KEY = "pg_governance_toggle";
    private static final String TEST_TARGET_ID = "pg-governance-target-001";

    @Autowired
    private ContentGovernanceRepository contentGovernanceRepository;

    @Autowired
    private ModerationPolicyRepository moderationPolicyRepository;

    @Autowired
    private SensitiveTermRepository sensitiveTermRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM content_report_actions WHERE report_id IN (SELECT id FROM content_reports WHERE target_id = ?)",
                TEST_TARGET_ID
        );
        jdbcTemplate.update("DELETE FROM content_reports WHERE target_id = ?", TEST_TARGET_ID);
        jdbcTemplate.update("DELETE FROM sensitive_terms WHERE term IN (?, ?, ?)", TEST_TERM, TEST_TERM + "_UPDATED", TEST_TERM + "_BATCH");
        jdbcTemplate.update("DELETE FROM moderation_policies WHERE policy_key = ?", TEST_POLICY_KEY);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", REPORTER_USER_ID, OPERATOR_USER_ID);
    }

    @Test
    void policyAndSensitiveTermRepositoriesShouldWorkWithGovernanceBaselineTablesInPostgres() {
        assertThat(columnType("is_whitelist")).isEqualTo("boolean");
        assertThat(columnType("enabled")).isEqualTo("boolean");

        assertThat(moderationPolicyRepository.findPolicyMap())
                .containsEntry("ai_input_enabled", "true")
                .containsEntry("auto_hide_report_threshold", "3");

        moderationPolicyRepository.upsertPolicy(TEST_POLICY_KEY, "false", "PG baseline test toggle", OPERATOR_USER_ID);
        moderationPolicyRepository.upsertPolicy(TEST_POLICY_KEY, "true", "PG baseline test toggle updated", OPERATOR_USER_ID);
        assertThat(moderationPolicyRepository.findPolicyMap())
                .containsEntry(TEST_POLICY_KEY, "true");

        long termId = sensitiveTermRepository.insertSensitiveTerm(
                TEST_TERM,
                "OTHER",
                "HIGH",
                "BLOCK",
                "AI_OUTPUT",
                false,
                true
        );
        assertThat(sensitiveTermRepository.findSensitiveTermId(TEST_TERM, "AI_OUTPUT", false))
                .contains(termId);
        assertThat(sensitiveTermRepository.findEnabledTermsForSource("AI_OUTPUT"))
                .extracting(SensitiveTermRow::term)
                .contains(TEST_TERM);

        assertThat(sensitiveTermRepository.updateSensitiveTerm(
                termId,
                TEST_TERM + "_UPDATED",
                "POLITICS",
                "MEDIUM",
                "REVIEW",
                "AI_OUTPUT",
                true,
                false
        )).isEqualTo(1);
        assertThat(sensitiveTermRepository.findSensitiveTerm(termId))
                .hasValueSatisfying(row -> {
                    assertThat(row.term()).isEqualTo(TEST_TERM + "_UPDATED");
                    assertThat(row.termType()).isEqualTo("POLITICS");
                    assertThat(row.riskLevel()).isEqualTo("MEDIUM");
                    assertThat(row.action()).isEqualTo("REVIEW");
                    assertThat(row.whitelist()).isTrue();
                    assertThat(row.enabled()).isFalse();
                });

        assertThat(sensitiveTermRepository.updateSensitiveTermsEnabled(java.util.List.of(termId), true)).isEqualTo(1);
        assertThat(sensitiveTermRepository.listSensitiveTerms())
                .extracting(SensitiveTermRow::term)
                .contains(TEST_TERM + "_UPDATED");
        assertThat(sensitiveTermRepository.findEnabledTermsForSource("AI_OUTPUT"))
                .extracting(SensitiveTermRow::term)
                .contains(TEST_TERM + "_UPDATED");

        long batchDeleteTermId = sensitiveTermRepository.insertSensitiveTerm(
                TEST_TERM + "_BATCH",
                "OTHER",
                "LOW",
                "MASK",
                "AI_OUTPUT",
                false,
                true
        );
        assertThat(sensitiveTermRepository.batchDeleteSensitiveTerms(java.util.List.of(batchDeleteTermId))).isEqualTo(1);
        assertThat(sensitiveTermRepository.findSensitiveTerm(batchDeleteTermId)).isEmpty();

        assertThat(sensitiveTermRepository.deleteSensitiveTerm(termId)).isEqualTo(1);
        assertThat(sensitiveTermRepository.findSensitiveTerm(termId)).isEmpty();
    }

    @Test
    void legacyReportActionRepositoryShouldStillWorkWithGovernanceBaselineTablesInPostgres() {
        insertUser(REPORTER_USER_ID, "STUDENT", "pg-governance-reporter@example.com", "Governance Reporter");
        insertUser(OPERATOR_USER_ID, "ADMIN", "pg-governance-admin@example.com", "Governance Admin");

        long reportId = contentGovernanceRepository.insertReport(
                REPORTER_USER_ID,
                "POST",
                TEST_TARGET_ID,
                "SPAM",
                "PG baseline governance report"
        );
        contentGovernanceRepository.insertReportAction(
                reportId,
                OPERATOR_USER_ID,
                "ACCEPT",
                "HIDE_TARGET",
                "PG baseline action"
        );

        assertThat(contentGovernanceRepository.listReportActions(reportId))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.operatorUserId()).isEqualTo(OPERATOR_USER_ID);
                    assertThat(action.operatorDisplayName()).isEqualTo("Governance Admin");
                    assertThat(action.decision()).isEqualTo("ACCEPT");
                    assertThat(action.action()).isEqualTo("HIDE_TARGET");
                    assertThat(action.comment()).isEqualTo("PG baseline action");
                });
    }

    private void insertUser(long userId, String role, String email, String displayName) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    id,
                    email,
                    password_hash,
                    role,
                    tier,
                    status,
                    display_name,
                    real_name,
                    is_deleted,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                role,
                displayName,
                displayName + " Real"
        );
    }

    private String columnType(String columnName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'sensitive_terms'
                   AND column_name = ?
                """,
                String.class,
                columnName
        );
    }
}
