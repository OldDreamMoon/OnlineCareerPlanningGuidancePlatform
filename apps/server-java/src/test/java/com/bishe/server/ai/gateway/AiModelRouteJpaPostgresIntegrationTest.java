package com.bishe.server.ai.gateway;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 模型路由仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import({
        AiProviderConfigRepository.class,
        AiModelRouteRepository.class
})
class AiModelRouteJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiProviderConfigRepository aiProviderConfigRepository;

    @Autowired
    private AiModelRouteRepository aiModelRouteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_model_routes");
        jdbcTemplate.update("DELETE FROM ai_provider_configs");
    }

    @Test
    void aiModelRouteRepositoryShouldSupportOrderingLookupAndUpdateOnPostgres() {
        long providerId = aiProviderConfigRepository.insertProvider(
                "GEMINI_MAIN",
                "GEMINI_NATIVE",
                "Gemini Main",
                "https://gemini.example.com/v1beta",
                "cipher-main",
                "sk-m***in",
                true,
                16000,
                2,
                new BigDecimal("0.002048"),
                new BigDecimal("0.017070"),
                "{\"authMode\":\"BEARER_TOKEN\"}"
        );

        long fallbackRouteId = aiModelRouteRepository.insertRoute(
                "RESUME_FALLBACK",
                "RESUME",
                null,
                null,
                providerId,
                "gemini-2.5-flash",
                100,
                80,
                "SYNC_BLOCKING",
                true,
                new BigDecimal("0.20"),
                null,
                null,
                "{\"scope\":\"fallback\"}"
        );
        long primaryRouteId = aiModelRouteRepository.insertRoute(
                "RESUME_PRIMARY",
                "RESUME",
                "RESUME_OPTIMIZE",
                null,
                providerId,
                "gemini-2.5-flash",
                5,
                100,
                "SYNC_BLOCKING",
                true,
                new BigDecimal("0.25"),
                "system prompt",
                "RESUME_TEMPLATE",
                "{\"scope\":\"primary\"}"
        );

        assertThat(aiModelRouteRepository.findAllRoutes())
                .extracting(AiModelRouteRepository.ModelRouteRow::routeCode)
                .containsExactly("RESUME_PRIMARY", "RESUME_FALLBACK");

        assertThat(aiModelRouteRepository.findRouteByCode("RESUME_PRIMARY"))
                .isPresent()
                .get()
                .satisfies(route -> {
                    assertThat(route.id()).isEqualTo(primaryRouteId);
                    assertThat(route.sceneCode()).isEqualTo("RESUME_OPTIMIZE");
                    assertThat(route.promptTemplateName()).isEqualTo("RESUME_TEMPLATE");
                });

        assertThat(aiModelRouteRepository.updateRoute(
                fallbackRouteId,
                "RESUME_FALLBACK",
                "RESUME",
                "",
                null,
                providerId,
                "gemini-2.5-flash-lite",
                90,
                70,
                "SYNC_BLOCKING",
                false,
                new BigDecimal("0.10"),
                "fallback prompt",
                "RESUME_TEMPLATE",
                "{\"scope\":\"fallback-updated\"}"
        )).isTrue();

        assertThat(aiModelRouteRepository.findRouteById(fallbackRouteId))
                .isPresent()
                .get()
                .satisfies(route -> {
                    assertThat(route.modelName()).isEqualTo("gemini-2.5-flash-lite");
                    assertThat(route.enabled()).isFalse();
                    assertThat(route.temperature()).isEqualByComparingTo("0.10");
                    assertThat(route.extraConfigJson()).contains("fallback-updated");
                    assertThat(route.updatedAt()).isNotNull();
                });
    }
}
