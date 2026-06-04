package com.bishe.server.adminconsole;

import com.bishe.server.ai.gateway.AiGatewayAdminService;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.governance.ModerationPoliciesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminConsoleSnapshotCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillConsoleSnapshotOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AdminSystemConsoleService.ConsoleSnapshotPayload payload = buildPayload(Instant.parse("2026-03-30T12:30:00Z").toEpochMilli(), "MOCK");
        when(valueOperations.get("admin:system:console-snapshot"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        AdminConsoleSnapshotCacheService service = new AdminConsoleSnapshotCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        AdminSystemConsoleService.ConsoleSnapshotPayload cached = service.getSnapshot(() -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.featureFlags()).hasSize(1);
        assertThat(cached.featureFlags().getFirst().currentValue()).isEqualTo("MOCK");
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictConsoleSnapshotImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        AdminConsoleSnapshotCacheService service = new AdminConsoleSnapshotCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit();

        verify(redisTemplate).delete("admin:system:console-snapshot");
    }

    private AdminSystemConsoleService.ConsoleSnapshotPayload buildPayload(Long generatedAt, String paymentMode) {
        return new AdminSystemConsoleService.ConsoleSnapshotPayload(
                generatedAt,
                List.of(new FeatureFlagService.FeatureFlagItem(
                        "payment.mode",
                        "支付模式",
                        "控制支付模式。",
                        "ENUM",
                        List.of("MOCK", "SANDBOX"),
                        paymentMode,
                        "MOCK",
                        true,
                        1L,
                        Instant.parse("2026-03-30T12:00:00Z")
                )),
                new AiGatewayAdminService.RuntimeSettingsPayload(
                        true,
                        true,
                        false,
                        false,
                        "MEDIUM",
                        1024,
                        "standard",
                        Instant.parse("2026-03-30T12:10:00Z").toEpochMilli()
                ),
                new ModerationPoliciesResponse(true, true, true, 3),
                new AdminSystemConsoleService.AiChannelOverviewPayload(
                        2,
                        1,
                        1,
                        0,
                        0,
                        1,
                        0,
                        3,
                        2,
                        1,
                        1,
                        1,
                        1,
                        0,
                        2,
                        1,
                        1,
                        0,
                        List.of(new AdminSystemConsoleService.AiChannelProviderItem(
                                1L,
                                "OPENAI_MAIN",
                                "OpenAI 主通道",
                                "OPENAI_COMPATIBLE",
                                true,
                                "HEALTHY",
                                "99.0%",
                                120,
                                680,
                                Instant.parse("2026-03-30T12:25:00Z").toEpochMilli()
                        ))
                )
        );
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
