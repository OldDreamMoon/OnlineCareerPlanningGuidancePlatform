package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderErrorMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapHttpError_shouldKeepProviderStatusAndFriendlyMessageFor429() {
        ApiException exception = AiProviderErrorMapper.mapHttpError(
                objectMapper,
                429,
                """
                        {
                          "error": {
                            "message": "Rate limit exceeded",
                            "status": "RESOURCE_EXHAUSTED"
                          }
                        }
                        """,
                new RuntimeException("upstream 429")
        );

        assertThat(exception.getCode()).isEqualTo("AI-2001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exception.getMessage()).isEqualTo("AI 服务当前请求过多，请稍后再试。");
        assertThat(exception.getData()).isInstanceOf(Map.class);
        Map<?, ?> data = (Map<?, ?>) exception.getData();
        assertThat(data.get("providerStatus")).isEqualTo(429);
        assertThat(data.get("retryable")).isEqualTo(true);
        assertThat(data.get("providerCategory")).isEqualTo("RATE_LIMIT");
        assertThat(data.get("providerMessage")).isEqualTo("Rate limit exceeded");
    }

    @Test
    void mapTransportError_shouldExposeTimeoutCategory() {
        ApiException exception = AiProviderErrorMapper.mapTransportError(new SocketTimeoutException("Read timed out"));

        assertThat(exception.getCode()).isEqualTo("AI-2001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exception.getMessage()).isEqualTo("AI 服务响应超时，请稍后重试。");
        assertThat(exception.getData()).isInstanceOf(Map.class);
        Map<?, ?> data = (Map<?, ?>) exception.getData();
        assertThat(data.get("retryable")).isEqualTo(true);
        assertThat(data.get("providerCategory")).isEqualTo("TIMEOUT");
        assertThat(data.get("providerMessage")).isEqualTo("Read timed out");
    }
}
