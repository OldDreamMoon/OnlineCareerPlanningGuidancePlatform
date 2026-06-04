package com.bishe.server.ai.gateway;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 提供商模型配置仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import({
        AiProviderConfigRepository.class,
        AiProviderModelRepository.class
})
class AiProviderModelJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiProviderConfigRepository aiProviderConfigRepository;

    @Autowired
    private AiProviderModelRepository aiProviderModelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_provider_models");
        jdbcTemplate.update("DELETE FROM ai_provider_configs");
    }

    @Test
    void aiProviderModelRepositoryShouldSupportReplaceOrderingAndBatchLookupOnPostgres() {
        long geminiProviderId = aiProviderConfigRepository.insertProvider(
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
        long openAiProviderId = aiProviderConfigRepository.insertProvider(
                "OPENAI_BACKUP",
                "OPENAI_COMPATIBLE",
                "OpenAI Backup",
                "https://backup.example.com/v1",
                "cipher-backup",
                "sk-b***up",
                false,
                12000,
                1,
                new BigDecimal("0.120000"),
                new BigDecimal("0.360000"),
                "{\"region\":\"backup\"}"
        );

        aiProviderModelRepository.replaceProviderModels(geminiProviderId, List.of(
                new AiProviderModelRepository.ProviderModelMutation(
                        "gemini-2.5-pro",
                        "Gemini 2.5 Pro",
                        false,
                        new BigDecimal("0.010000"),
                        new BigDecimal("0.030000"),
                        1048576,
                        8192,
                        "[\"RESUME\"]",
                        "disabled backup"
                ),
                new AiProviderModelRepository.ProviderModelMutation(
                        "gemini-2.5-flash",
                        "Gemini 2.5 Flash",
                        true,
                        new BigDecimal("0.002048"),
                        new BigDecimal("0.017070"),
                        1048576,
                        8192,
                        "[\"RESUME\",\"INTERVIEW_TEXT\"]",
                        "primary text model"
                )
        ));
        aiProviderModelRepository.replaceProviderModels(openAiProviderId, List.of(
                new AiProviderModelRepository.ProviderModelMutation(
                        "gpt-4o-mini",
                        "GPT-4o Mini",
                        true,
                        new BigDecimal("0.150000"),
                        new BigDecimal("0.600000"),
                        128000,
                        4096,
                        "[\"RESUME\"]",
                        "backup model"
                )
        ));

        assertThat(aiProviderModelRepository.findProviderModelsByProviderId(geminiProviderId))
                .extracting(AiProviderModelRepository.ProviderModelRow::modelCode)
                .containsExactly("gemini-2.5-flash", "gemini-2.5-pro");

        assertThat(aiProviderModelRepository.findProviderModelsByProviderIds(List.of(openAiProviderId, geminiProviderId)))
                .extracting(row -> row.providerConfigId() + ":" + row.modelCode())
                .containsExactly(
                        geminiProviderId + ":gemini-2.5-flash",
                        geminiProviderId + ":gemini-2.5-pro",
                        openAiProviderId + ":gpt-4o-mini"
                );

        aiProviderModelRepository.replaceProviderModels(geminiProviderId, List.of(
                new AiProviderModelRepository.ProviderModelMutation(
                        "gemini-2.5-flash-lite",
                        "Gemini 2.5 Flash Lite",
                        true,
                        new BigDecimal("0.001000"),
                        new BigDecimal("0.008000"),
                        524288,
                        4096,
                        "[\"RESUME\",\"PORTRAIT_SUMMARY\"]",
                        "replaced set"
                )
        ));

        assertThat(aiProviderModelRepository.findProviderModelsByProviderId(geminiProviderId))
                .singleElement()
                .satisfies(model -> {
                    assertThat(model.modelCode()).isEqualTo("gemini-2.5-flash-lite");
                    assertThat(model.inputCostPer1k()).isEqualByComparingTo("0.001000");
                    assertThat(model.supportedTaskTypesJson()).contains("PORTRAIT_SUMMARY");
                    assertThat(model.updatedAt()).isNotNull();
                });
    }
}
