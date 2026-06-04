package com.bishe.server.ai.gateway;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 场景路由策略仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(SceneRoutePolicyRepository.class)
class SceneRoutePolicyJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SceneRoutePolicyRepository sceneRoutePolicyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_scene_route_policies");
    }

    @Test
    void sceneRoutePolicyRepositoryShouldSupportOrderingLookupAndUpdateOnPostgres() {
        long freePolicyId = sceneRoutePolicyRepository.insertRoutePolicy(
                "INTERVIEW_REPLY_FREE",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                "FREE",
                "SINGLE",
                true,
                "免费用户单路由",
                "{\"thinking\":{\"reasoningEffort\":\"MEDIUM\"}}"
        );
        long allPolicyId = sceneRoutePolicyRepository.insertRoutePolicy(
                "INTERVIEW_REPLY_ALL",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                "ALL",
                "FAILOVER",
                true,
                "默认回退策略",
                null
        );
        long premiumPolicyId = sceneRoutePolicyRepository.insertRoutePolicy(
                "INTERVIEW_REPLY_PREMIUM",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                "PREMIUM",
                "WEIGHTED",
                false,
                "高级会员策略",
                "{\"thinking\":{\"thinkingBudget\":2048}}"
        );

        assertThat(sceneRoutePolicyRepository.findAllRoutePolicies())
                .extracting(SceneRoutePolicyRepository.SceneRoutePolicyRow::policyCode)
                .containsExactly("INTERVIEW_REPLY_ALL", "INTERVIEW_REPLY_FREE", "INTERVIEW_REPLY_PREMIUM");

        assertThat(sceneRoutePolicyRepository.findRoutePolicyBySceneAndTier("INTERVIEW_TEXT", "INTERVIEW_REPLY", "PREMIUM"))
                .isPresent()
                .get()
                .satisfies(policy -> {
                    assertThat(policy.id()).isEqualTo(premiumPolicyId);
                    assertThat(policy.strategyType()).isEqualTo("WEIGHTED");
                    assertThat(policy.enabled()).isFalse();
                });

        assertThat(sceneRoutePolicyRepository.findRoutePolicyByCode("INTERVIEW_REPLY_ALL"))
                .isPresent()
                .get()
                .satisfies(policy -> assertThat(policy.id()).isEqualTo(allPolicyId));

        assertThat(sceneRoutePolicyRepository.updateRoutePolicy(
                freePolicyId,
                "INTERVIEW_REPLY_FREE",
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                "FREE",
                "FAILOVER",
                true,
                "免费用户故障转移",
                "{\"thinking\":{\"thinkingLevel\":\"high\"}}"
        )).isTrue();

        assertThat(sceneRoutePolicyRepository.findRoutePolicyById(freePolicyId))
                .isPresent()
                .get()
                .satisfies(policy -> {
                    assertThat(policy.strategyType()).isEqualTo("FAILOVER");
                    assertThat(policy.notes()).isEqualTo("免费用户故障转移");
                    assertThat(policy.extraConfigJson()).contains("thinkingLevel");
                    assertThat(policy.updatedAt()).isNotNull();
                });
    }
}
