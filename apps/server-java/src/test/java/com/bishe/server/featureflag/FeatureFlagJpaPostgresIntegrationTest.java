package com.bishe.server.featureflag;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 功能开关仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(FeatureFlagRepository.class)
class FeatureFlagJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM feature_flags");
    }

    @Test
    void featureFlagRepositoryShouldSupportUpsertAndReadOnPostgres() {
        assertThat(featureFlagRepository.findAll()).isEmpty();
        assertThat(featureFlagRepository.findByKey("payment.enabled")).isEmpty();

        featureFlagRepository.upsertFlag("payment.enabled", "true", "支付总开关", 101L);
        featureFlagRepository.upsertFlag("payment.mode", "SANDBOX", "支付模式", 102L);

        assertThat(featureFlagRepository.findByKey("payment.enabled"))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.flagValue()).isEqualTo("true");
                    assertThat(row.description()).isEqualTo("支付总开关");
                    assertThat(row.updatedBy()).isEqualTo(101L);
                    assertThat(row.updatedAt()).isNotNull();
                });

        assertThat(featureFlagRepository.findAll())
                .extracting(FeatureFlagRepository.FeatureFlagRow::flagKey)
                .containsExactly("payment.mode", "payment.enabled");

        featureFlagRepository.upsertFlag("payment.enabled", "false", "支付总开关-关闭", 103L);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feature_flags WHERE flag_key = ?", Integer.class, "payment.enabled"))
                .isEqualTo(1);
        assertThat(featureFlagRepository.findByKey("payment.enabled"))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.flagValue()).isEqualTo("false");
                    assertThat(row.description()).isEqualTo("支付总开关-关闭");
                    assertThat(row.updatedBy()).isEqualTo(103L);
                });
    }
}
