package com.bishe.server.ai.interview;

import com.bishe.server.ai.dto.InterviewSessionCreateResponse;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.tx.AfterCommitActionSynchronization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * AI 面试创建去重：对同一用户的短窗口重复创建请求做 Redis 防重与结果复用。
 */
@Service
public class AiInterviewCreateGuardService {

    private static final Logger log = LoggerFactory.getLogger(AiInterviewCreateGuardService.class);
    private static final String RESULT_KEY_PREFIX = "ai:interview:create:result:";
    private static final String LOCK_KEY_PREFIX = "ai:interview:create:lock:";
    private static final Duration RESULT_TTL = Duration.ofMinutes(2);
    private static final Duration LOCK_TTL = Duration.ofSeconds(45);
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration WAIT_POLL_INTERVAL = Duration.ofMillis(150);

    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AiInterviewCreateGuardService(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public GuardDecision begin(long userId, Object fingerprintPayload) {
        if (userId <= 0 || fingerprintPayload == null) {
            return GuardDecision.proceed(null);
        }
        String keySuffix = buildKeySuffix(userId, fingerprintPayload);
        if (keySuffix == null || stringRedisTemplate == null) {
            return GuardDecision.proceed(keySuffix);
        }

        InterviewSessionCreateResponse cached = readResult(resultKey(keySuffix));
        if (cached != null) {
            return GuardDecision.cached(keySuffix, cached);
        }

        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey(keySuffix), "1", LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return GuardDecision.proceed(keySuffix);
            }
        } catch (Exception ex) {
            log.debug("acquire ai interview create guard lock failed: {}", ex.getMessage());
            return GuardDecision.proceed(keySuffix);
        }

        InterviewSessionCreateResponse waited = waitForResult(resultKey(keySuffix));
        if (waited != null) {
            return GuardDecision.cached(keySuffix, waited);
        }
        return GuardDecision.inFlight(keySuffix);
    }

    public void rememberAfterCommit(String keySuffix, InterviewSessionCreateResponse response) {
        runAfterCommit(() -> rememberNow(keySuffix, response));
    }

    public void releaseNow(String keySuffix) {
        if (stringRedisTemplate == null || keySuffix == null || keySuffix.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(lockKey(keySuffix));
        } catch (Exception ex) {
            log.debug("release ai interview create guard lock failed: {}", ex.getMessage());
        }
    }

    private void rememberNow(String keySuffix, InterviewSessionCreateResponse response) {
        if (stringRedisTemplate == null || keySuffix == null || keySuffix.isBlank() || response == null) {
            releaseNow(keySuffix);
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    resultKey(keySuffix),
                    objectMapper.writeValueAsString(response),
                    RESULT_TTL
            );
        } catch (Exception ex) {
            log.debug("write ai interview create guard result failed: {}", ex.getMessage());
        } finally {
            releaseNow(keySuffix);
        }
    }

    private InterviewSessionCreateResponse waitForResult(String resultKey) {
        Instant deadline = Instant.now().plus(WAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            InterviewSessionCreateResponse response = readResult(resultKey);
            if (response != null) {
                return response;
            }
            try {
                Thread.sleep(WAIT_POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return readResult(resultKey);
    }

    private InterviewSessionCreateResponse readResult(String key) {
        if (stringRedisTemplate == null || key == null || key.isBlank()) {
            return null;
        }
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, InterviewSessionCreateResponse.class);
        } catch (Exception ex) {
            log.debug("read ai interview create guard result failed: {}", ex.getMessage());
            return null;
        }
    }

    private String buildKeySuffix(long userId, Object fingerprintPayload) {
        try {
            String scope = userRepository.findById(userId)
                    .map(user -> "user-" + user.id() + "-" + (user.createdAt() == null ? 0L : user.createdAt().toEpochMilli()))
                    .orElse("user-" + userId);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fingerprintBytes = digest.digest(objectMapper.writeValueAsBytes(fingerprintPayload));
            return scope + ":" + HexFormat.of().formatHex(fingerprintBytes);
        } catch (Exception ex) {
            log.debug("build ai interview create guard key failed: {}", ex.getMessage());
            try {
                String raw = String.valueOf(fingerprintPayload);
                String normalized = raw.trim().toUpperCase(Locale.ROOT);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] fingerprintBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
                return "user-" + userId + ":" + HexFormat.of().formatHex(fingerprintBytes);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String resultKey(String keySuffix) {
        return RESULT_KEY_PREFIX + keySuffix;
    }

    private String lockKey(String keySuffix) {
        return LOCK_KEY_PREFIX + keySuffix;
    }

    private void runAfterCommit(Runnable action) {
        AfterCommitActionSynchronization.registerOrRun(action);
    }

    public record GuardDecision(State state, String keySuffix, InterviewSessionCreateResponse cachedResponse) {

        public static GuardDecision proceed(String keySuffix) {
            return new GuardDecision(State.PROCEED, keySuffix, null);
        }

        public static GuardDecision cached(String keySuffix, InterviewSessionCreateResponse response) {
            return new GuardDecision(State.CACHED, keySuffix, response);
        }

        public static GuardDecision inFlight(String keySuffix) {
            return new GuardDecision(State.IN_FLIGHT, keySuffix, null);
        }

        public boolean shouldProceed() {
            return state == State.PROCEED;
        }

        public boolean shouldReturnCached() {
            return state == State.CACHED && cachedResponse != null;
        }
    }

    public enum State {
        PROCEED,
        CACHED,
        IN_FLIGHT
    }
}
