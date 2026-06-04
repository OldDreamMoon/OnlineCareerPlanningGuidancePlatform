package com.bishe.server.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminWorkbenchCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillWorkbenchPayloadOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        AdminDashboardService.WorkbenchPayload payload = buildPayload(java.time.Instant.parse("2026-03-30T12:00:00Z").toEpochMilli());
        when(valueOperations.get(anyString()))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        AdminWorkbenchCacheService service = new AdminWorkbenchCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        AdminDashboardService.WorkbenchPayload cached = service.getWorkbench(24, "Asia/Shanghai", () -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.pendingReports()).isEqualTo(3L);
        assertThat(cached.providerRuntime().timezone()).isEqualTo("Asia/Shanghai");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), any(Duration.class));
        verify(setOperations).add("admin:dashboard:workbench:index", keyCaptor.getValue());
        verify(redisTemplate).expire(eq("admin:dashboard:workbench:index"), any(Duration.class));
    }

    @Test
    void shouldEvictIndexedWorkbenchKeysImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("admin:dashboard:workbench:index"))
                .thenReturn(Set.of("admin:dashboard:workbench:a", "admin:dashboard:workbench:b"));

        AdminWorkbenchCacheService service = new AdminWorkbenchCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(Set.of("admin:dashboard:workbench:a", "admin:dashboard:workbench:b"));
        verify(redisTemplate).delete("admin:dashboard:workbench:index");
    }

    private AdminDashboardService.WorkbenchPayload buildPayload(Long generatedAt) {
        return new AdminDashboardService.WorkbenchPayload(
                generatedAt,
                3L,
                2L,
                1L,
                4L,
                10L,
                new AdminDashboardService.ProviderRuntimeSummaryPayload(
                        24,
                        "Asia/Shanghai",
                        6L,
                        4L,
                        1L,
                        0L,
                        1L,
                        2L,
                        2L
                )
        );
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
