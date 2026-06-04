package com.bishe.server.certification;

import com.bishe.server.certification.dto.CertificationAssetResponse;
import com.bishe.server.certification.dto.CertificationReviewDetailResponse;
import com.bishe.server.certification.dto.CertificationSubmissionResponse;
import com.bishe.server.certification.service.CertificationReviewDetailCacheService;
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

class CertificationReviewDetailCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillReviewDetailOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CertificationReviewDetailResponse payload = buildPayload();
        String cacheKey = "certification:reviews:detail:2001";
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        CertificationReviewDetailCacheService service = new CertificationReviewDetailCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        CertificationReviewDetailResponse cached = service.getReviewDetail(2001L, this::buildPayload);

        assertThat(cached).isNotNull();
        assertThat(cached.userId()).isEqualTo(2001L);
        assertThat(cached.currentSubmission()).isNotNull();
        assertThat(cached.currentSubmission().companyName()).isEqualTo("审核详情缓存企业");
        assertThat(cached.submissions()).hasSize(2);
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictReviewDetailImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CertificationReviewDetailCacheService service = new CertificationReviewDetailCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(2001L);

        verify(redisTemplate).delete("certification:reviews:detail:2001");
    }

    private CertificationReviewDetailResponse buildPayload() {
        CertificationSubmissionResponse currentSubmission = new CertificationSubmissionResponse(
                3002L,
                2001L,
                "ENTERPRISE",
                "王小明",
                "审核详情缓存企业",
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
                        "certification/detail-current.pdf",
                        "detail-current-proof.pdf",
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
                "审核详情历史企业",
                "招聘负责人",
                "REPLACED",
                false,
                "请补充新的证明材料。",
                null,
                java.time.Instant.parse("2026-03-29T11:00:00Z").toEpochMilli(),
                java.time.Instant.parse("2026-03-29T14:00:00Z").toEpochMilli(),
                List.of(new CertificationAssetResponse(
                        4001L,
                        "test-bucket",
                        "certification/detail-previous.pdf",
                        "detail-previous-proof.pdf",
                        "application/pdf",
                        2048L,
                        "REPLACED",
                        "REPLACED_BY_NEW_SUBMISSION",
                        java.time.Instant.parse("2026-03-29T11:00:00Z").toEpochMilli(),
                        java.time.Instant.parse("2026-03-30T12:30:00Z").toEpochMilli()
                ))
        );
        return new CertificationReviewDetailResponse(
                2001L,
                "review-detail@example.com",
                "审核详情企业",
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
