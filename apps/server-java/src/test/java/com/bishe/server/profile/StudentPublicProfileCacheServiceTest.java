package com.bishe.server.profile;

import com.bishe.server.profile.dto.StudentProfileResponse;
import com.bishe.server.profile.dto.StudentProfileSocialLinkItem;
import com.bishe.server.profile.service.StudentPublicProfileCacheService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentPublicProfileCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillPublicProfileSliceOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        StudentPublicProfileCacheService.StudentPublicProfileSlice payload = buildSlice(18L, "缓存学生");
        when(valueOperations.get("student:profile:public:18"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        StudentPublicProfileCacheService service = new StudentPublicProfileCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        StudentPublicProfileCacheService.StudentPublicProfileSlice cached = service.getPublicProfile(18L, () -> payload);

        assertThat(cached).isNotNull();
        assertThat(cached.displayName()).isEqualTo("缓存学生");
        assertThat(cached.skillTags()).containsExactly("React", "TypeScript");
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictPublicProfileImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        StudentPublicProfileCacheService service = new StudentPublicProfileCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit(25L);

        verify(redisTemplate).delete("student:profile:public:25");
    }

    private StudentPublicProfileCacheService.StudentPublicProfileSlice buildSlice(long userId, String displayName) {
        StudentProfileResponse.VisibilityItem visibleToAll = new StudentProfileResponse.VisibilityItem(true, true, true, true, true);
        StudentProfileResponse.PrivacySettingsPayload privacy = new StudentProfileResponse.PrivacySettingsPayload(
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll,
                visibleToAll
        );
        StudentProfileResponse.PortraitPayload portrait = new StudentProfileResponse.PortraitPayload(
                List.of(new StudentProfileResponse.PortraitTagItem("COMMUNITY_ACTIVE", "社区互动积极", "COMMUNITY", 0.82)),
                List.of("社区互动积极"),
                List.of("有效成长信号仍偏少"),
                "NORMAL",
                "RECENT",
                "你在前端方向已经出现社区互动积极信号，可以继续把优势做深。",
                "当前画像聚焦在前端开发工程师方向，已经显现社区互动积极的优势信号。",
                List.of("继续整理最近一轮项目表达，并补齐结果数据。"),
                "TEMPLATE_V1",
                new StudentProfileResponse.PortraitEvidencePayload(2, 1, 3, 1, 2, 5),
                1743321600000L
        );
        return new StudentPublicProfileCacheService.StudentPublicProfileSlice(
                userId,
                displayName,
                "FREE",
                "林同学",
                "积极求职中",
                "华东理工大学",
                "华东理工大学",
                "软件工程",
                "2026届",
                "3.8 / 4.0",
                "前端开发工程师",
                "国家励志奖学金",
                List.of("React", "TypeScript"),
                "持续准备前端求职。",
                new StudentProfileResponse.AvatarPayload(true, "image/png", 1743321600000L),
                List.of(new StudentProfileSocialLinkItem("GITHUB", "lin-frontend")),
                privacy,
                portrait,
                9L
        );
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
