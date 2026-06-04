package com.bishe.server.consult.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * 支付运行态防重：对支付回调、关单、退款等外部副作用操作加短 TTL Redis 锁与完成标记。
 */
@Service
public class ConsultPaymentOperationGuardService {

    private static final Logger log = LoggerFactory.getLogger(ConsultPaymentOperationGuardService.class);
    private static final String DONE_KEY_PREFIX = "consult:payment:guard:done:";
    private static final String LOCK_KEY_PREFIX = "consult:payment:guard:lock:";
    private static final Duration DONE_TTL = Duration.ofMinutes(2);
    private static final Duration LOCK_TTL = Duration.ofSeconds(45);
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration WAIT_POLL_INTERVAL = Duration.ofMillis(150);

    private final StringRedisTemplate stringRedisTemplate;

    public ConsultPaymentOperationGuardService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public GuardDecision begin(Operation operation, String scope) {
        String keySuffix = buildKeySuffix(operation, scope);
        if (keySuffix == null || stringRedisTemplate == null) {
            return GuardDecision.proceed();
        }
        if (hasDone(doneKey(keySuffix))) {
            return GuardDecision.done();
        }
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey(keySuffix), "1", LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return GuardDecision.proceed();
            }
        } catch (Exception ex) {
            log.debug("acquire consult payment operation guard lock failed: {}", ex.getMessage());
            return GuardDecision.proceed();
        }
        if (waitForDone(doneKey(keySuffix))) {
            return GuardDecision.done();
        }
        return GuardDecision.inFlight();
    }

    public void markDoneAfterCommit(Operation operation, String scope) {
        String keySuffix = buildKeySuffix(operation, scope);
        if (keySuffix == null) {
            return;
        }
        runAfterCommit(() -> markDoneNow(keySuffix));
    }

    public void releaseNow(Operation operation, String scope) {
        String keySuffix = buildKeySuffix(operation, scope);
        if (keySuffix == null || stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(lockKey(keySuffix));
        } catch (Exception ex) {
            log.debug("release consult payment operation guard lock failed: {}", ex.getMessage());
        }
    }

    private void markDoneNow(String keySuffix) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(doneKey(keySuffix), "1", DONE_TTL);
        } catch (Exception ex) {
            log.debug("mark consult payment operation guard done failed: {}", ex.getMessage());
        } finally {
            releaseKeySuffix(keySuffix);
        }
    }

    private boolean hasDone(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            return payload != null && !payload.isBlank();
        } catch (Exception ex) {
            log.debug("read consult payment operation done marker failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean waitForDone(String doneKey) {
        Instant deadline = Instant.now().plus(WAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (hasDone(doneKey)) {
                return true;
            }
            try {
                Thread.sleep(WAIT_POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return hasDone(doneKey);
    }

    private String buildKeySuffix(Operation operation, String scope) {
        if (operation == null || scope == null || scope.isBlank()) {
            return null;
        }
        return operation.code() + ":" + scope.trim().toUpperCase(Locale.ROOT);
    }

    private String doneKey(String keySuffix) {
        return DONE_KEY_PREFIX + keySuffix;
    }

    private String lockKey(String keySuffix) {
        return LOCK_KEY_PREFIX + keySuffix;
    }

    private void releaseKeySuffix(String keySuffix) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(lockKey(keySuffix));
        } catch (Exception ex) {
            log.debug("release consult payment operation guard lock by key suffix failed: {}", ex.getMessage());
        }
    }

    private void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    public record GuardDecision(State state) {

        public static GuardDecision proceed() {
            return new GuardDecision(State.PROCEED);
        }

        public static GuardDecision done() {
            return new GuardDecision(State.DONE);
        }

        public static GuardDecision inFlight() {
            return new GuardDecision(State.IN_FLIGHT);
        }

        public boolean shouldProceed() {
            return state == State.PROCEED;
        }

        public boolean shouldUseDoneState() {
            return state == State.DONE;
        }
    }

    public enum Operation {
        CALLBACK_SUCCESS("CALLBACK_SUCCESS"),
        CLOSE_TRADE("CLOSE_TRADE"),
        REFUND("REFUND");

        private final String code;

        Operation(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public enum State {
        PROCEED,
        DONE,
        IN_FLIGHT
    }
}
