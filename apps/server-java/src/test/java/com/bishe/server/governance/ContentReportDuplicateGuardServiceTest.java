package com.bishe.server.governance;

import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentReportDuplicateGuardServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillDuplicateReportSnapshotOnCacheMissAndEvict() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userRepository.findById(8L)).thenReturn(Optional.of(new AppUser(
                8L,
                "reporter@example.com",
                "hash",
                UserRole.STUDENT,
                "FREE",
                UserAccountStatus.ACTIVE,
                "Reporter",
                Instant.parse("2024-03-30T07:00:00Z")
        )));
        String key = "governance:report:dedupe:user-8-1711782000000:POST:123:ABUSE";
        when(valueOperations.get(key))
                .thenReturn(null, objectMapper.writeValueAsString(new SnapshotPayload(77L, "PENDING")));

        ContentReportDuplicateGuardService service = new ContentReportDuplicateGuardService(
                providerOf(redisTemplate),
                userRepository,
                objectMapper
        );

        ContentGovernanceRepository.ExistingReportRow existing = service.findDuplicate(
                8L,
                "POST",
                "123",
                "ABUSE",
                () -> Optional.of(new ContentGovernanceRepository.ExistingReportRow(77L, "PENDING"))
        );

        assertThat(existing).isNotNull();
        assertThat(existing.reportId()).isEqualTo(77L);
        verify(valueOperations).set(eq(key), anyString(), any(Duration.class));

        service.evictAfterCommit(8L, "POST", "123", "ABUSE");

        verify(redisTemplate).delete(key);
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }

    private record SnapshotPayload(long reportId, String status) {
    }
}
