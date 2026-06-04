package com.bishe.server.mentor;

import com.bishe.server.mentor.service.MentorPublicDetailCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MentorPublicDetailCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillMentorPublicDetailOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        MentorPublicDetailCacheService.MentorPublicDetailSnapshot snapshot =
                new MentorPublicDetailCacheService.MentorPublicDetailSnapshot(
                        12L,
                        "Mentor Cache",
                        null,
                        false,
                        "Cache Corp",
                        "Staff Engineer",
                        "https://example.com/avatar.png",
                        null,
                        Instant.parse("2026-03-30T10:00:00Z"),
                        List.of("系统设计", "后端"),
                        List.of("模拟面试复盘"),
                        "擅长高并发与项目表达。",
                        "适合有项目经历的同学。",
                        "不适合零基础同学。",
                        "准备简历与 JD。",
                        "工作日晚间回复。",
                        9900,
                        List.of(),
                        new BigDecimal("4.80"),
                        32,
                        true
                );
        when(valueOperations.get("mentor:detail:public:12"))
                .thenReturn(null, objectMapper.writeValueAsString(snapshot));

        MentorPublicDetailCacheService service = new MentorPublicDetailCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        MentorPublicDetailCacheService.MentorPublicDetailSnapshot cached =
                service.getPublicDetail(12L, () -> snapshot);

        assertThat(cached.userId()).isEqualTo(12L);
        assertThat(cached.displayName()).isEqualTo("Mentor Cache");
        assertThat(cached.totalOrders()).isEqualTo(32);
        verify(valueOperations).set(eq("mentor:detail:public:12"), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        MentorPublicDetailCacheService service = new MentorPublicDetailCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(9L);

        verify(redisTemplate).delete("mentor:detail:public:9");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
