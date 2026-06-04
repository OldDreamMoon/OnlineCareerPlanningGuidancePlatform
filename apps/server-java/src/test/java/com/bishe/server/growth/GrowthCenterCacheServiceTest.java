package com.bishe.server.growth;

import com.bishe.server.growth.dto.DailyTaskItemResponse;
import com.bishe.server.growth.dto.GrowthCheckinOverviewResponse;
import com.bishe.server.growth.service.GrowthCenterCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthCenterCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillCheckinOverviewOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        GrowthCheckinOverviewResponse payload = new GrowthCheckinOverviewResponse(
                false,
                7,
                2,
                "2026-03-29",
                "2026-03",
                31,
                List.of(28, 29),
                10,
                List.of(new GrowthCheckinOverviewResponse.RewardRuleItem(
                        "CHECKIN_STREAK_3",
                        "三日连签奖励",
                        "连续签到 3 天可额外获得 6 积分。",
                        3,
                        6
                )),
                new GrowthCheckinOverviewResponse.NextRewardItem(
                        "CHECKIN_STREAK_3",
                        "三日连签奖励",
                        "连续签到 3 天可额外获得 6 积分。",
                        3,
                        6,
                        1
                )
        );
        String cacheKey = "growth:checkin:overview:2001:2026-03-30";
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        GrowthCenterCacheService service = new GrowthCenterCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        GrowthCheckinOverviewResponse cached = service.getCheckinOverview(
                2001L,
                LocalDate.of(2026, 3, 30),
                () -> payload
        );

        assertThat(cached).isNotNull();
        assertThat(cached.currentStreak()).isEqualTo(2);
        assertThat(cached.checkedInDays()).containsExactly(28, 29);
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));
    }

    @Test
    void shouldBackfillDailyTasksOnCacheMissAndEvictCurrentDay() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        List<DailyTaskItemResponse> payload = List.of(
                new DailyTaskItemResponse(1L, "TASK_RESUME_OPTIMIZE", "完成一次简历优化", "整理一版简历", 10, false),
                new DailyTaskItemResponse(2L, "TASK_SKILL_PROGRESS", "点亮一个技能节点", "更新技能学习进度", 8, true)
        );
        String cacheKey = "growth:daily-tasks:2001:2026-03-30";
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        GrowthCenterCacheService service = new GrowthCenterCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        List<DailyTaskItemResponse> cached = service.getDailyTasks(
                2001L,
                LocalDate.of(2026, 3, 30),
                () -> payload
        );

        assertThat(cached).hasSize(2);
        assertThat(cached.get(1).completed()).isTrue();
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));

        service.evictDailyTasksAfterCommit(2001L);

        verify(redisTemplate).delete("growth:daily-tasks:2001:" + LocalDate.now());
    }

    @Test
    void shouldBackfillPointsLedgerSummaryOnCacheMissAndEvictCurrentDay() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        GrowthCenterCacheService.PointsLedgerSummarySnapshot payload =
                new GrowthCenterCacheService.PointsLedgerSummarySnapshot(36, 4);
        String cacheKey = "growth:points-ledger-summary:2001:2026-03-30";
        when(valueOperations.get(cacheKey))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        GrowthCenterCacheService service = new GrowthCenterCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        GrowthCenterCacheService.PointsLedgerSummarySnapshot cached = service.getPointsLedgerSummary(
                2001L,
                LocalDate.of(2026, 3, 30),
                () -> payload
        );

        assertThat(cached.balance()).isEqualTo(36);
        assertThat(cached.total()).isEqualTo(4);
        verify(valueOperations).set(eq(cacheKey), anyString(), any(Duration.class));

        service.evictPointsLedgerSummaryAfterCommit(2001L);

        verify(redisTemplate).delete("growth:points-ledger-summary:2001:" + LocalDate.now());
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
