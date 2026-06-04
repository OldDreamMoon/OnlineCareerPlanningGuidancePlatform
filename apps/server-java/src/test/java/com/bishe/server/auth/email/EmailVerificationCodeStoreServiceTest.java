package com.bishe.server.auth.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationCodeStoreServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ObjectProvider<StringRedisTemplate> emptyRedisProvider =
            new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class);

    @Test
    void shouldStoreReadAndDeleteRecordWithLocalFallback() {
        EmailVerificationCodeStoreService service = new EmailVerificationCodeStoreService(objectMapper, emptyRedisProvider);
        byte[] codeHash = "demo-hash".getBytes(StandardCharsets.UTF_8);
        Instant expiresAt = Instant.now().plusSeconds(120).truncatedTo(ChronoUnit.MILLIS);
        Instant nextSendAt = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS);

        service.put("student@example.com", codeHash, expiresAt, nextSendAt);

        Optional<EmailVerificationCodeStoreService.EmailCodeRecord> record = service.get("student@example.com");
        assertThat(record).isPresent();
        assertThat(record.orElseThrow().codeHash()).isEqualTo(codeHash);
        assertThat(record.orElseThrow().expiresAt()).isEqualTo(expiresAt);
        assertThat(record.orElseThrow().nextSendAt()).isEqualTo(nextSendAt);

        service.delete("student@example.com");

        assertThat(service.get("student@example.com")).isEmpty();
    }

    @Test
    void shouldTreatExpiredLocalRecordAsMissing() {
        EmailVerificationCodeStoreService service = new EmailVerificationCodeStoreService(objectMapper, emptyRedisProvider);

        service.put(
                "expired@example.com",
                "demo-hash".getBytes(StandardCharsets.UTF_8),
                Instant.now().minusSeconds(5),
                Instant.now().minusSeconds(10)
        );

        assertThat(service.get("expired@example.com")).isEmpty();
    }
}
