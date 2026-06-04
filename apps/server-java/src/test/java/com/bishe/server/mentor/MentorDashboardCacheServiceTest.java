package com.bishe.server.mentor;

import com.bishe.server.mentor.dto.MentorDashboardResponse;
import com.bishe.server.mentor.service.MentorDashboardCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MentorDashboardCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillMentorDashboardOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        MentorDashboardResponse payload = new MentorDashboardResponse(
                2,
                1,
                6,
                18800,
                7,
                new BigDecimal("4.90"),
                List.of(new MentorDashboardResponse.RecentOrderItem(
                        "ORD001",
                        9L,
                        "Student Cache",
                        8800,
                        "PAID",
                        "想请导师看一下简历。",
                        java.time.Instant.parse("2026-03-30T09:00:00Z").toEpochMilli(),
                        java.time.Instant.parse("2026-03-30T09:10:00Z").toEpochMilli(),
                        null
                ))
        );
        when(valueOperations.get("mentor:dashboard:6"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        MentorDashboardCacheService service = new MentorDashboardCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        MentorDashboardResponse cached = service.getDashboard(6L, () -> payload);

        assertThat(cached.pendingPaidCount()).isEqualTo(2);
        assertThat(cached.recentOrders()).hasSize(1);
        assertThat(cached.recentOrders().getFirst().status()).isEqualTo("PAID");
        verify(valueOperations).set(eq("mentor:dashboard:6"), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        MentorDashboardCacheService service = new MentorDashboardCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(15L);

        verify(redisTemplate).delete("mentor:dashboard:15");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
