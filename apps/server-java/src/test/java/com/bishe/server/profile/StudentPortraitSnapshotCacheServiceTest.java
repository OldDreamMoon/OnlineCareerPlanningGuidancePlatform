package com.bishe.server.profile;

import com.bishe.server.profile.repository.StudentProfileRepository;
import com.bishe.server.profile.service.StudentPortraitSnapshotCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentPortraitSnapshotCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillStudentPortraitSnapshotOnCacheMissAndEvict() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        StudentProfileRepository.PortraitSnapshotRow payload = new StudentProfileRepository.PortraitSnapshotRow(
                "[{\"code\":\"COMMUNITY_ACTIVE\",\"label\":\"社区互动积极\"}]",
                "{\"posts7d\":2}",
                Instant.parse("2026-03-30T15:00:00Z")
        );
        when(valueOperations.get("student:portrait:snapshot:18"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        StudentPortraitSnapshotCacheService service = new StudentPortraitSnapshotCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        StudentProfileRepository.PortraitSnapshotRow cached = service.getSnapshot(18L, () -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.portraitTagsJson()).contains("COMMUNITY_ACTIVE");
        verify(valueOperations).set(eq("student:portrait:snapshot:18"), anyString(), any(Duration.class));

        service.evictAfterCommit(18L);

        verify(redisTemplate).delete("student:portrait:snapshot:18");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
