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
 * AI 提供商配置仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(AiProviderConfigRepository.class)
class AiProviderConfigJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiProviderConfigRepository aiProviderConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_provider_configs");
    }

    @Test
    void aiProviderConfigRepositoryShouldSupportOrderingLookupAndUpdateOnPostgres() {
        long disabledId = aiProviderConfigRepository.insertProvider(
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
        long enabledId = aiProviderConfigRepository.insertProvider(
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

        assertThat(aiProviderConfigRepository.findAllProviders())
                .extracting(AiProviderConfigRepository.ProviderConfigRow::providerCode)
                .containsExactly("GEMINI_MAIN", "OPENAI_BACKUP");

        assertThat(aiProviderConfigRepository.findProviderByCode("GEMINI_MAIN"))
                .isPresent()
                .get()
                .satisfies(provider -> {
                    assertThat(provider.id()).isEqualTo(enabledId);
                    assertThat(provider.enabled()).isTrue();
                    assertThat(provider.costPer1kInput()).isEqualByComparingTo("0.002048");
                });

        assertThat(aiProviderConfigRepository.updateProvider(
                disabledId,
                "OPENAI_BACKUP",
                "OPENAI_COMPATIBLE",
                "OpenAI Backup V2",
                "https://backup2.example.com/v1",
                "cipher-backup-v2",
                "sk-b***v2",
                true,
                18000,
                3,
                new BigDecimal("0.100000"),
                new BigDecimal("0.300000"),
                "{\"region\":\"backup-2\"}"
        )).isTrue();

        assertThat(aiProviderConfigRepository.findProviderById(disabledId))
                .isPresent()
                .get()
                .satisfies(provider -> {
                    assertThat(provider.displayName()).isEqualTo("OpenAI Backup V2");
                    assertThat(provider.baseUrl()).isEqualTo("https://backup2.example.com/v1");
                    assertThat(provider.enabled()).isTrue();
                    assertThat(provider.timeoutMs()).isEqualTo(18000);
                    assertThat(provider.maxRetries()).isEqualTo(3);
                    assertThat(provider.extraConfigJson()).contains("backup-2");
                    assertThat(provider.updatedAt()).isNotNull();
                });
    }
}
