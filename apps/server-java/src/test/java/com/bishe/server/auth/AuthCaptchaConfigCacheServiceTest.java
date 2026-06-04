package com.bishe.server.auth;

import com.bishe.server.auth.captcha.AuthCaptchaConfigCacheService;
import com.bishe.server.auth.dto.AuthCaptchaConfigResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthCaptchaConfigCacheServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBackfillCaptchaConfigOnCacheMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AuthCaptchaConfigResponse payload = new AuthCaptchaConfigResponse(
                true,
                "GEETEST_V4",
                "captcha-id",
                "bind",
                300L,
                true,
                true,
                true,
                true
        );
        when(valueOperations.get("auth:captcha:public-config"))
                .thenReturn(null, objectMapper.writeValueAsString(payload));

        AuthCaptchaConfigCacheService service = new AuthCaptchaConfigCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        AuthCaptchaConfigResponse cached = service.getPublicConfig(() -> payload);

        assertThat(cached.enabled()).isTrue();
        assertThat(cached.captchaId()).isEqualTo("captcha-id");
        verify(valueOperations).set(eq("auth:captcha:public-config"), anyString(), any(Duration.class));
    }

    @Test
    void shouldEvictImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        AuthCaptchaConfigCacheService service = new AuthCaptchaConfigCacheService(
                objectMapper,
                providerOf(redisTemplate)
        );

        service.evictAfterCommit();

        verify(redisTemplate).delete("auth:captcha:public-config");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
