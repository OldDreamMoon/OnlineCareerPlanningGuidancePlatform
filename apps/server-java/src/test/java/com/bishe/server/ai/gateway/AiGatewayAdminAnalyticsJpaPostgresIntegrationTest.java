package com.bishe.server.ai.gateway;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 管理后台分析仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(AiGatewayAdminAnalyticsRepository.class)
class AiGatewayAdminAnalyticsJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long USER_A = 981L;
    private static final long USER_B = 982L;

    @Autowired
    private AiGatewayAdminAnalyticsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertUser(USER_A, "ai-analytics-a@example.com", "Analytics A");
        insertUser(USER_B, "ai-analytics-b@example.com", "Analytics B");
        jdbcTemplate.update("DELETE FROM ai_call_logs WHERE user_id IN (?, ?)", USER_A, USER_B);
    }

    @Test
    void analyticsRepositoryShouldSupportLogsAndMetricsOnPostgres() {
        Instant now = Instant.parse("2026-04-16T15:30:00Z");
        Instant later = now.plusSeconds(300);
        Instant startAt = now.minusSeconds(3600);
        Instant endAt = later.plusSeconds(3600);

        insertCallLog(
                "trc_pg_analytics_001",
                USER_A,
                "INTERVIEW_TEXT",
                "INTERVIEW_REPLY",
                "OPENAI_DEMO_MAIN",
                "gpt-4o-mini",
                1800L,
                "SUCCESS",
                120,
                60,
                180,
                new BigDecimal("0.819360"),
                5,
                1,
                "文本面试追问成功",
                "{\"ok\":true}",
                "FREE",
                now
        );
        insertCallLog(
                "trc_pg_analytics_002",
                USER_B,
                "STT",
                "INTERVIEW_VOICE_TRANSCRIBE",
                "GEMINI_NATIVE_CANDIDATE",
                "gemini-2.5-flash",
                2200L,
                "SUCCESS",
                40,
                20,
                60,
                new BigDecimal("0.204840"),
                0,
                1,
                "语音转写成功",
                "{\"transcript\":\"你好\"}",
                "PRO",
                later
        );

        assertThat(repository.findAiLogs(null, null, null, null, null, null, 1, 1))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.traceId()).isEqualTo("trc_pg_analytics_001");
                    assertThat(row.userEmail()).isEqualTo("ai-analytics-a@example.com");
                });

        assertThat(repository.findAiLogs(null, null, "OPENAI_DEMO_MAIN", null, null, null, 10, 0))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.traceId()).isEqualTo("trc_pg_analytics_001");
                    assertThat(row.userDisplayName()).isEqualTo("Analytics A");
                    assertThat(row.estimatedCost()).isEqualByComparingTo("0.819360");
                });

        assertThat(repository.countAiLogs(null, null, null, null, null, null)).isEqualTo(2);
        assertThat(repository.countAiLogs(null, null, "OPENAI_DEMO_MAIN", null, null, null)).isEqualTo(1);

        long logId = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_call_logs WHERE trace_id = ?",
                Long.class,
                "trc_pg_analytics_001"
        );
        assertThat(repository.findAiLogDetail(logId))
                .isPresent()
                .get()
                .satisfies(detail -> {
                    assertThat(detail.traceId()).isEqualTo("trc_pg_analytics_001");
                    assertThat(detail.userEmail()).isEqualTo("ai-analytics-a@example.com");
                    assertThat(detail.resultPayloadJson()).contains("\"ok\":true");
                });

        assertThat(repository.summarizeOverview(Timestamp.from(startAt), Timestamp.from(endAt)))
                .satisfies(overview -> {
                    assertThat(overview.totalCalls()).isEqualTo(2);
                    assertThat(overview.totalCost()).isEqualByComparingTo("1.024200");
                });

        assertThat(repository.findTrafficLogs(Timestamp.from(startAt), Timestamp.from(endAt)))
                .hasSize(2)
                .extracting(AiGatewayAdminAnalyticsRepository.HourlyTrafficLogRow::status)
                .containsExactly("SUCCESS", "SUCCESS");

        assertThat(repository.findProviderRuntimeLogs(Timestamp.from(startAt), Timestamp.from(endAt)))
                .hasSize(2)
                .extracting(AiGatewayAdminAnalyticsRepository.ProviderRuntimeLogRow::provider)
                .containsExactly("OPENAI_DEMO_MAIN", "GEMINI_NATIVE_CANDIDATE");

        assertThat(repository.summarizeByModel(Timestamp.from(startAt), Timestamp.from(endAt)))
                .extracting(AiGatewayAdminAnalyticsRepository.GroupedMetricRow::name)
                .containsExactly("gpt-4o-mini", "gemini-2.5-flash");

        assertThat(repository.summarizeByProvider(Timestamp.from(startAt), Timestamp.from(endAt)).getFirst())
                .satisfies(row -> {
                    assertThat(row.name()).isEqualTo("OPENAI_DEMO_MAIN");
                    assertThat(row.calls()).isEqualTo(1);
                    assertThat(row.cost()).isEqualByComparingTo("0.819360");
                });

        assertThat(repository.summarizeByTaskType(Timestamp.from(startAt), Timestamp.from(endAt)))
                .extracting(AiGatewayAdminAnalyticsRepository.GroupedMetricRow::name)
                .containsExactly("INTERVIEW_TEXT", "STT");

        assertThat(repository.summarizeByTier(Timestamp.from(startAt), Timestamp.from(endAt)))
                .extracting(AiGatewayAdminAnalyticsRepository.GroupedMetricRow::name)
                .containsExactly("FREE", "PRO");

        assertThat(repository.summarizeTopUsers(Timestamp.from(startAt), Timestamp.from(endAt), 10).getFirst())
                .satisfies(row -> {
                    assertThat(row.userId()).isEqualTo(USER_A);
                    assertThat(row.userEmail()).isEqualTo("ai-analytics-a@example.com");
                    assertThat(row.cost()).isEqualByComparingTo("0.819360");
                });

        assertThat(repository.summarizeByScene(Timestamp.from(startAt), Timestamp.from(endAt), 10))
                .extracting(AiGatewayAdminAnalyticsRepository.SceneMetricRow::sceneCode)
                .containsExactly("INTERVIEW_REPLY", "INTERVIEW_VOICE_TRANSCRIBE");

        assertThat(repository.summarizeByScene(Timestamp.from(startAt), Timestamp.from(endAt), 10).getFirst())
                .satisfies(row -> {
                    assertThat(row.taskType()).isEqualTo("INTERVIEW_TEXT");
                    assertThat(row.calls()).isEqualTo(1);
                    assertThat(row.successCalls()).isEqualTo(1);
                    assertThat(row.avgLatencyMs()).isEqualTo(1800L);
                    assertThat(row.totalCost()).isEqualByComparingTo("0.819360");
                    assertThat(row.lastCallAt()).isEqualTo(now);
                });
    }

    private void insertCallLog(
            String traceId,
            long userId,
            String taskType,
            String sceneCode,
            String provider,
            String model,
            long latencyMs,
            String status,
            int requestTokens,
            int responseTokens,
            int totalTokens,
            BigDecimal estimatedCost,
            int chargedPoints,
            int quotaWeight,
            String resultSummary,
            String resultPayloadJson,
            String userTier,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_call_logs(
                    trace_id, user_id, task_type, scene_code, provider, model, route_code, route_policy_code,
                    latency_ms, status, error_code, request_tokens, response_tokens, total_tokens, thoughts_tokens,
                    reasoning_effort, thinking_budget, thinking_level, estimated_cost, charged_points, quota_weight,
                    result_summary, result_payload_json, user_deleted_at, user_tier, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                traceId,
                userId,
                taskType,
                sceneCode,
                provider,
                model,
                "route-" + traceId,
                "policy-" + traceId,
                latencyMs,
                status,
                null,
                requestTokens,
                responseTokens,
                totalTokens,
                0,
                "LOW",
                64,
                "BALANCED",
                estimatedCost,
                chargedPoints,
                quotaWeight,
                resultSummary,
                resultPayloadJson,
                null,
                userTier,
                Timestamp.from(createdAt)
        );
    }

    private void insertUser(long userId, String email, String displayName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
        );
        if (existing != null && existing > 0) {
            return;
        }

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
                )
                VALUES (?, ?, ?, 'STUDENT', 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                displayName,
                displayName
        );
    }
}
