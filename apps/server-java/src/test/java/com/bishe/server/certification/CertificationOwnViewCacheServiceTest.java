package com.bishe.server.certification;

import com.bishe.server.certification.dto.CertificationAssetResponse;
import com.bishe.server.certification.dto.CertificationOwnViewResponse;
import com.bishe.server.certification.dto.CertificationSubmissionResponse;
import com.bishe.server.certification.service.CertificationOwnViewCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificationOwnViewCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillOwnViewOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CertificationOwnViewResponse payload = buildPayload();
        String cacheKey = "certification:own-view:2001";
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        CertificationOwnViewCacheService service = new CertificationOwnViewCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        CertificationOwnViewResponse cached = service.getOwnView(2001L, this::buildPayload);

        assertThat(cached).isNotNull();
        assertThat(cached.userId()).isEqualTo(2001L);
        assertThat(cached.currentSubmission()).isNotNull();
        assertThat(cached.currentSubmission().companyName()).isEqualTo("缓存企业");
        assertThat(cached.submissions()).hasSize(2);
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictOwnViewImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CertificationOwnViewCacheService service = new CertificationOwnViewCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(2001L);

        verify(redisTemplate).delete("certification:own-view:2001");
    }

    private CertificationOwnViewResponse buildPayload() {
        CertificationSubmissionResponse currentSubmission = new CertificationSubmissionResponse(
                3002L,
                2001L,
                "ENTERPRISE",
                "王小明",
                "缓存企业",
                "招聘负责人",
                "PENDING",
                true,
                null,
                3001L,
                java.time.Instant.parse("2026-03-30T12:30:00Z").toEpochMilli(),
                null,
                List.of(new CertificationAssetResponse(
                        4002L,
                        "test-bucket",
                        "certification/current.pdf",
                        "current-proof.pdf",
                        "application/pdf",
                        1024L,
                        "ACTIVE",
                        null,
                        java.time.Instant.parse("2026-03-30T12:30:00Z").toEpochMilli(),
                        null
                ))
        );
        CertificationSubmissionResponse historicalSubmission = new CertificationSubmissionResponse(
                3001L,
                2001L,
                "ENTERPRISE",
                "王小明",
                "历史企业",
                "招聘负责人",
                "REPLACED",
                false,
                "请补充新版材料。",
                null,
                java.time.Instant.parse("2026-03-29T11:00:00Z").toEpochMilli(),
                java.time.Instant.parse("2026-03-29T14:00:00Z").toEpochMilli(),
                List.of(new CertificationAssetResponse(
                        4001L,
                        "test-bucket",
                        "certification/previous.pdf",
                        "previous-proof.pdf",
                        "application/pdf",
                        2048L,
                        "REPLACED",
                        "REPLACED_BY_NEW_SUBMISSION",
                        java.time.Instant.parse("2026-03-29T11:00:00Z").toEpochMilli(),
                        java.time.Instant.parse("2026-03-30T12:30:00Z").toEpochMilli()
                ))
        );
        return new CertificationOwnViewResponse(
                2001L,
                "ENTERPRISE",
                "PENDING",
                currentSubmission,
                List.of(currentSubmission, historicalSubmission)
        );
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
