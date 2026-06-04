package com.bishe.server.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOperationsDashboardCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillOperationsPayloadOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AdminDashboardService.OperationsDashboardPayload payload = buildPayload("week", java.time.Instant.parse("2026-03-30T12:40:00Z").toEpochMilli());
        when(valueOperations.get("admin:dashboard:operations:week"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        AdminOperationsDashboardCacheService service = new AdminOperationsDashboardCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        AdminDashboardService.OperationsDashboardPayload cached = service.getOperationsDashboard("week", () -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.period()).isEqualTo("week");
        assertThat(cached.overview().newStudents()).isEqualTo(12L);
        assertThat(cached.activationRate().value()).isEqualTo("50%");
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictAllOperationsPeriodKeysImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        AdminOperationsDashboardCacheService service = new AdminOperationsDashboardCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(java.util.List.of(
                "admin:dashboard:operations:today",
                "admin:dashboard:operations:week",
                "admin:dashboard:operations:month"
        ));
    }

    private AdminDashboardService.OperationsDashboardPayload buildPayload(String period, Long generatedAt) {
        return new AdminDashboardService.OperationsDashboardPayload(
                period,
                generatedAt,
                java.time.Instant.parse("2026-03-24T00:00:00Z").toEpochMilli(),
                java.time.Instant.parse("2026-03-30T23:59:59Z").toEpochMilli(),
                new AdminDashboardService.OverviewPayload(12, 6, 20, 4, 30, 24, 10, 5, 8, 2, 3, 200, 140, 70, 40),
                new AdminDashboardService.MetricPayload("激活率", "50%", 6, 12, "note"),
                new AdminDashboardService.MetricPayload("7日留存", "40%", 4, 10, "note"),
                new AdminDashboardService.MetricPayload("咨询转化率", "20%", 4, 20, "note"),
                new AdminDashboardService.MetricPayload("AI 调用成功率", "80%", 24, 30, "note"),
                new AdminDashboardService.MetricPayload("社区 AI 覆盖率", "50%", 5, 10, "note"),
                new AdminDashboardService.MetricPayload("内容审查拦截率", "25%", 2, 8, "note"),
                new AdminDashboardService.DurationMetricPayload("举报处置时效中位数", "24h", 3, "note"),
                new AdminDashboardService.MetricPayload("画像完整率", "70%", 140, 200, "note"),
                new AdminDashboardService.MetricPayload("社区榜单覆盖率", "57.1%", 40, 70, "note")
        );
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
