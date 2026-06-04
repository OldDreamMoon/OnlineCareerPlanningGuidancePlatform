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
 * AI 网关运行时设置仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(AiGatewayRuntimeSettingRepository.class)
class AiGatewayRuntimeSettingJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiGatewayRuntimeSettingRepository aiGatewayRuntimeSettingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_gateway_runtime_settings");
    }

    @Test
    void runtimeSettingRepositoryShouldSupportOrderedReadAndUpsertOnPostgres() {
        assertThat(aiGatewayRuntimeSettingRepository.findAll()).isEmpty();

        aiGatewayRuntimeSettingRepository.upsert(
                "DEFAULT_REASONING_EFFORT",
                "HIGH",
                "控制全局默认思考强度；留空时沿用代码内置推荐。",
                301L
        );
        aiGatewayRuntimeSettingRepository.upsert(
                "DEBUG_MODE_ENABLED",
                "true",
                "控制 AI 网关后端诊断日志输出。",
                302L
        );

        assertThat(aiGatewayRuntimeSettingRepository.findAll())
                .extracting(AiGatewayRuntimeSettingRepository.RuntimeSettingRow::settingKey)
                .containsExactly("DEBUG_MODE_ENABLED", "DEFAULT_REASONING_EFFORT");

        aiGatewayRuntimeSettingRepository.upsert(
                "DEBUG_MODE_ENABLED",
                "false",
                "控制 AI 网关后端诊断日志输出。",
                303L
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_gateway_runtime_settings WHERE setting_key = ?",
                Integer.class,
                "DEBUG_MODE_ENABLED"
        )).isEqualTo(1);

        assertThat(aiGatewayRuntimeSettingRepository.findAll())
                .filteredOn(row -> "DEBUG_MODE_ENABLED".equals(row.settingKey()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.settingValue()).isEqualTo("false");
                    assertThat(row.updatedBy()).isEqualTo(303L);
                    assertThat(row.updatedAt()).isNotNull();
                });
    }
}
