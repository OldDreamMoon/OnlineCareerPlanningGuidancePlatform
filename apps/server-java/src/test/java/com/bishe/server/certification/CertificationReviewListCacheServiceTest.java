package com.bishe.server.certification;

import com.bishe.server.certification.dto.CertificationReviewListResponse;
import com.bishe.server.certification.service.CertificationReviewListCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
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

class CertificationReviewListCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillReviewListOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        CertificationReviewListResponse payload = buildPayload();
        String cacheKey = "certification:reviews:list:" + DigestUtils.md5DigestAsHex(
                "缓存企业|enterprise|pending|1|10".getBytes(StandardCharsets.UTF_8)
        );
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        CertificationReviewListCacheService service = new CertificationReviewListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        CertificationReviewListResponse cached = service.getReviewList("缓存企业", "ENTERPRISE", "PENDING", 1, 10, this::buildPayload);

        assertThat(cached).isNotNull();
        assertThat(cached.records()).hasSize(1);
        assertThat(cached.records().getFirst().companyName()).isEqualTo("缓存企业");
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));
        verify(setOperations).add("certification:reviews:list:index", cacheKey);
    }

    @Test
    void shouldEvictIndexedReviewListKeysImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("certification:reviews:list:index"))
                .thenReturn(Set.of("certification:reviews:list:a", "certification:reviews:list:b"));

        CertificationReviewListCacheService service = new CertificationReviewListCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAllAfterCommit();

        verify(redisTemplate).delete(Set.of("certification:reviews:list:a", "certification:reviews:list:b"));
        verify(redisTemplate).delete("certification:reviews:list:index");
    }

    private CertificationReviewListResponse buildPayload() {
        return new CertificationReviewListResponse(
                List.of(new CertificationReviewListResponse.ReviewItem(
                        2001L,
                        "cache-enterprise@example.com",
                        "认证缓存企业",
                        "ENTERPRISE",
                        "PENDING",
                        3001L,
                        "PENDING",
                        "王小明",
                        "缓存企业",
                        "招聘负责人",
                        1,
                        "cache-proof.pdf",
                        java.time.Instant.parse("2026-03-30T12:00:00Z").toEpochMilli(),
                        null
                )),
                1L,
                1,
                10
        );
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
