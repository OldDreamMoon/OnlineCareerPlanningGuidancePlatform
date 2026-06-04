package com.bishe.server.mentor;

import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotListResponse;
import com.bishe.server.mentor.schedule.dto.MentorScheduleSlotResponse;
import com.bishe.server.mentor.schedule.service.MentorPublicScheduleCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

class MentorPublicScheduleCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillPublicScheduleOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        MentorScheduleSlotListResponse payload = new MentorScheduleSlotListResponse(
                java.util.List.of(new MentorScheduleSlotResponse(
                        17L,
                        6L,
                        java.time.Instant.parse("2099-03-01T09:00:00Z").toEpochMilli(),
                        java.time.Instant.parse("2099-03-01T10:00:00Z").toEpochMilli(),
                        "AVAILABLE",
                        null
                ))
        );
        String dataKey = "mentor:schedule:public:6:_:_";
        when(valueOperations.get(dataKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        MentorPublicScheduleCacheService service = new MentorPublicScheduleCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        MentorScheduleSlotListResponse cached = service.getPublicSlots(6L, null, null, () -> payload);

        assertThat(cached.records()).hasSize(1);
        assertThat(cached.records().getFirst().id()).isEqualTo(17L);
        verify(valueOperations).set(eq(dataKey), anyString(), any(Duration.class));
        verify(setOperations).add("mentor:schedule:public:index:6", dataKey);
        verify(redisTemplate).expire(eq("mentor:schedule:public:index:6"), any(Duration.class));
    }

    @Test
    void shouldEvictIndexedScheduleWindowsImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("mentor:schedule:public:index:8"))
                .thenReturn(Set.of("mentor:schedule:public:8:_:_", "mentor:schedule:public:8:1:2"));

        MentorPublicScheduleCacheService service = new MentorPublicScheduleCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(8L);

        verify(redisTemplate).delete(Set.of("mentor:schedule:public:8:_:_", "mentor:schedule:public:8:1:2"));
        verify(redisTemplate).delete("mentor:schedule:public:index:8");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
