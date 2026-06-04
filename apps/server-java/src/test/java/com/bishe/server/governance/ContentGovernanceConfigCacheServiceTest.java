package com.bishe.server.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentGovernanceConfigCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillPolicyMapOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("governance:config:policies"))
                .thenReturn(null, objectMapper.writeValueAsString(Map.of(
                        "ai_input_enabled", "true",
                        "ai_output_enabled", "false"
                )));

        ContentGovernanceConfigCacheService service = new ContentGovernanceConfigCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        Map<String, String> policyMap = service.getPolicyMap(() -> Map.of(
                "ai_input_enabled", "true",
                "ai_output_enabled", "false"
        ));

        assertThat(policyMap).containsEntry("ai_input_enabled", "true");
        assertThat(policyMap).containsEntry("ai_output_enabled", "false");
        verify(valueOperations).set(eq("governance:config:policies"), any(String.class), any(Duration.class));
    }

    @Test
    void shouldBackfillSensitiveTermsBySourceOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = "governance:config:sensitive-terms:source:AI_INPUT";
        List<SensitiveTermRow> rows = List.of(
                new SensitiveTermRow(
                        1L,
                        "违禁词",
                        "OTHER",
                        "HIGH",
                        "BLOCK",
                        "AI_INPUT",
                        false,
                        true,
                        Instant.parse("2026-03-30T08:00:00Z"),
                        Instant.parse("2026-03-30T08:00:00Z")
                )
        );
        when(valueOperations.get(key))
                .thenReturn(null, objectMapper.writeValueAsString(rows));

        ContentGovernanceConfigCacheService service = new ContentGovernanceConfigCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        List<SensitiveTermRow> cachedRows = service.getEnabledTermsForSource("ai_input", () -> rows);

        assertThat(cachedRows).hasSize(1);
        assertThat(cachedRows.getFirst().term()).isEqualTo("违禁词");
        verify(valueOperations).set(eq(key), any(String.class), any(Duration.class));
    }

    @Test
    void shouldEvictPoliciesAndSensitiveTermsImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        ContentGovernanceConfigCacheService service = new ContentGovernanceConfigCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictPoliciesAfterCommit();
        service.evictSensitiveTermsAfterCommit();

        verify(redisTemplate).delete("governance:config:policies");
        verify(redisTemplate).delete(anyList());
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
