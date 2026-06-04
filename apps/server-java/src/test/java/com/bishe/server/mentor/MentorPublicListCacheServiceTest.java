package com.bishe.server.mentor;

import com.bishe.server.mentor.dto.MentorListResponse;
import com.bishe.server.mentor.service.MentorPublicListCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MentorPublicListCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillPublicMentorListOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        MentorListResponse payload = new MentorListResponse(
                List.of(new MentorListResponse.MentorItem(
                        9L,
                        "ListCacheMentor",
                        null,
                        false,
                        "缓存实验室",
                        "前端导师",
                        "https://example.com/avatar.png",
                        List.of("React"),
                        List.of("简历诊断"),
                        "擅长公共列表缓存验证。",
                        8800,
                        new BigDecimal("4.80"),
                        12,
                        true,
                        false
                )),
                1,
                1,
                10
        );
        when(valueOperations.get(anyString()))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        MentorPublicListCacheService service = new MentorPublicListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        MentorListResponse cached = service.getMentorList(
                "缓存实验室",
                null,
                null,
                null,
                null,
                true,
                1,
                10,
                () -> payload
        );

        assertThat(cached.records()).hasSize(1);
        assertThat(cached.records().getFirst().userId()).isEqualTo(9L);
        assertThat(cached.records().getFirst().favorited()).isFalse();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), any(Duration.class));
        verify(setOperations).add("mentor:list:public:index", keyCaptor.getValue());
        verify(redisTemplate).expire(eq("mentor:list:public:index"), any(Duration.class));
    }

    @Test
    void shouldEvictIndexedListKeysImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("mentor:list:public:index"))
                .thenReturn(Set.of("mentor:list:public:a", "mentor:list:public:b"));

        MentorPublicListCacheService service = new MentorPublicListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(Set.of("mentor:list:public:a", "mentor:list:public:b"));
        verify(redisTemplate).delete("mentor:list:public:index");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
