package com.bishe.server.ai.interview;

import com.bishe.server.ai.dto.AiModerationPayload;
import com.bishe.server.ai.dto.InterviewSessionCreateResponse;
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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiInterviewCreateGuardServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldReturnCachedResponseWhenDuplicateResultAlreadyExists() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userRepository.findById(8L)).thenReturn(Optional.of(new AppUser(
                8L,
                "interview@example.com",
                "hash",
                UserRole.STUDENT,
                "FREE",
                UserAccountStatus.ACTIVE,
                "InterviewUser",
                Instant.parse("2024-03-30T07:00:00Z")
        )));

        InterviewSessionCreateResponse cachedResponse = new InterviewSessionCreateResponse(
                "is_cached_01",
                "INTERVIEW_TEXT",
                "请先做一个简短自我介绍。",
                0,
                100,
                5,
                8,
                new AiModerationPayload("AI_OUTPUT", "LOW", "PASS", "RULE_CLEAR")
        );
        String payloadJson = objectMapper.writeValueAsString(cachedResponse);
        when(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("ai:interview:create:result:user-8-1711782000000:")))
                .thenReturn(payloadJson);

        AiInterviewCreateGuardService service = new AiInterviewCreateGuardService(
                providerOf(redisTemplate),
                userRepository,
                objectMapper
        );

        AiInterviewCreateGuardService.GuardDecision decision = service.begin(
                8L,
                Map.of("targetRole", "Backend Engineer", "mode", "INTERVIEW_TEXT")
        );

        assertThat(decision.shouldReturnCached()).isTrue();
        assertThat(decision.cachedResponse()).isNotNull();
        assertThat(decision.cachedResponse().sessionId()).isEqualTo("is_cached_01");
        assertThat(decision.cachedResponse().firstQuestion()).isEqualTo("请先做一个简短自我介绍。");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
