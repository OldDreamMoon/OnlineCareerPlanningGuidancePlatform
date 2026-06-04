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
 * AI 提示词模板仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(PromptTemplateRepository.class)
class PromptTemplateJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PromptTemplateRepository promptTemplateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM prompt_templates");
    }

    @Test
    void promptTemplateRepositoryShouldSupportVersionOrderingActivationAndUpdateOnPostgres() {
        long v1 = promptTemplateRepository.insertPromptTemplate(
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY_CORE",
                1,
                "ACTIVE",
                "TEXT",
                "模板 v1",
                "描述 v1",
                "{\"version\":1}",
                null
        );
        long v2 = promptTemplateRepository.insertPromptTemplate(
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY_CORE",
                2,
                "DRAFT",
                "MESSAGE_BUNDLE",
                "模板 v2",
                "描述 v2",
                "{\"version\":2}",
                "{\"messages\":[]}"
        );

        assertThat(promptTemplateRepository.findAllPromptTemplates())
                .extracting(PromptTemplateRepository.PromptTemplateRow::versionNo)
                .containsExactly(2, 1);

        assertThat(promptTemplateRepository.findActivePromptTemplate("INTERVIEW_TEXT", "INTERVIEW_REPLY_CORE"))
                .isPresent()
                .get()
                .satisfies(template -> {
                    assertThat(template.id()).isEqualTo(v1);
                    assertThat(template.status()).isEqualTo("ACTIVE");
                });

        assertThat(promptTemplateRepository.updatePromptTemplate(
                v2,
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY_CORE",
                2,
                "ACTIVE",
                "MESSAGE_BUNDLE",
                "模板 v2 发布版",
                "描述 v2 发布版",
                "{\"version\":2,\"published\":true}",
                "{\"messages\":[{\"role\":\"system\"}]}"
        )).isTrue();
        promptTemplateRepository.deactivateOtherPromptTemplates("INTERVIEW_TEXT", "INTERVIEW_REPLY_CORE", v2);

        assertThat(promptTemplateRepository.findPromptTemplateById(v1))
                .isPresent()
                .get()
                .satisfies(template -> assertThat(template.status()).isEqualTo("INACTIVE"));

        assertThat(promptTemplateRepository.findActivePromptTemplate("INTERVIEW_TEXT", "INTERVIEW_REPLY_CORE"))
                .isPresent()
                .get()
                .satisfies(template -> {
                    assertThat(template.id()).isEqualTo(v2);
                    assertThat(template.content()).isEqualTo("模板 v2 发布版");
                    assertThat(template.templateFormat()).isEqualTo("MESSAGE_BUNDLE");
                    assertThat(template.bundleJson()).contains("system");
                    assertThat(template.updatedAt()).isNotNull();
                });
    }
}
