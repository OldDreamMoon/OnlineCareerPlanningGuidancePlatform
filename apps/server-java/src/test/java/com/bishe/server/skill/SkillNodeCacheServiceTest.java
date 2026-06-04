package com.bishe.server.skill;

import com.bishe.server.skill.repository.SkillRepository;
import com.bishe.server.skill.service.SkillNodeCacheService;
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

class SkillNodeCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillSkillNodesOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<SkillRepository.SkillNodeRow> nodes = List.of(
                new SkillRepository.SkillNodeRow("programming_language_foundations", "程序设计与语言基础", "建立语言基础", null, 100),
                new SkillRepository.SkillNodeRow("java_programming", "Java 程序设计", "掌握面向对象与标准库", "programming_language_foundations", 120)
        );
        when(valueOperations.get("skill:tree:nodes"))
                .thenReturn(null, objectMapper.writeValueAsString(nodes));

        SkillNodeCacheService service = new SkillNodeCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        List<SkillRepository.SkillNodeRow> cached = service.getAllNodes(() -> nodes);

        assertThat(cached).hasSize(2);
        assertThat(cached.getFirst().nodeCode()).isEqualTo("programming_language_foundations");
        verify(valueOperations).set(eq("skill:tree:nodes"), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        SkillNodeCacheService service = new SkillNodeCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit();

        verify(redisTemplate).delete("skill:tree:nodes");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
