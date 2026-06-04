package com.bishe.server.ai.quota;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 配额策略仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(AiQuotaPolicyRepository.class)
class AiQuotaPolicyJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiQuotaPolicyRepository aiQuotaPolicyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_quota_policies");
        jdbcTemplate.update(
                """
                INSERT INTO ai_quota_policies(
                    tier,
                    task_type,
                    scene_code,
                    daily_free_limit,
                    points_per_call,
                    daily_max_limit,
                    model_preference,
                    max_input_tokens,
                    created_at,
                    updated_at
                )
                VALUES
                    ('FREE', 'COMMUNITY_REPLY', NULL, 5, 2, 20, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('FREE', 'COMMUNITY_REPLY', 'COMMUNITY_PRE_ANSWER', 5, 2, 20, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('FREE', 'INTERVIEW_TEXT', NULL, 5, 5, 30, 'mock-economy-model', 4000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('PREMIUM', 'COMMUNITY_REPLY', NULL, -1, 0, -1, 'mock-premium-model', 12000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """
        );
    }

    @Test
    void quotaPolicyRepositoryShouldSupportSceneFallbackOrderingAndUpdateOnPostgres() {
        assertThat(aiQuotaPolicyRepository.findPolicy("FREE", "COMMUNITY_REPLY", "community_pre_answer"))
                .isPresent()
                .get()
                .satisfies(policy -> {
                    assertThat(policy.sceneCode()).isEqualTo("COMMUNITY_PRE_ANSWER");
                    assertThat(policy.dailyFreeLimit()).isEqualTo(5);
                });

        assertThat(aiQuotaPolicyRepository.findPolicy("FREE", "COMMUNITY_REPLY", "unknown-scene"))
                .isPresent()
                .get()
                .satisfies(policy -> {
                    assertThat(policy.sceneCode()).isNull();
                    assertThat(policy.pointsPerCall()).isEqualTo(2);
                });

        assertThat(aiQuotaPolicyRepository.findTaskFallbackPoliciesByTier("FREE"))
                .extracting(AiQuotaPolicyRepository.QuotaPolicyRow::taskType)
                .containsExactly("COMMUNITY_REPLY", "INTERVIEW_TEXT");

        assertThat(aiQuotaPolicyRepository.findAllPolicies())
                .extracting(AiQuotaPolicyRepository.QuotaPolicyRow::sceneCode)
                .containsExactly(null, "COMMUNITY_PRE_ANSWER", null, null);

        long policyId = aiQuotaPolicyRepository.findPolicy("FREE", "INTERVIEW_TEXT")
                .orElseThrow()
                .id();
        assertThat(aiQuotaPolicyRepository.updatePolicy(policyId, 8, 12, 40, "gpt-4o-mini", 8192)).isTrue();

        assertThat(aiQuotaPolicyRepository.findPolicyById(policyId))
                .isPresent()
                .get()
                .satisfies(policy -> {
                    assertThat(policy.dailyFreeLimit()).isEqualTo(8);
                    assertThat(policy.pointsPerCall()).isEqualTo(12);
                    assertThat(policy.dailyMaxLimit()).isEqualTo(40);
                    assertThat(policy.modelPreference()).isEqualTo("gpt-4o-mini");
                    assertThat(policy.maxInputTokens()).isEqualTo(8192);
                    assertThat(policy.updatedAt()).isNotNull();
                });
    }
}
