package com.bishe.server.consult;

import com.bishe.server.consult.service.ConsultPaymentOperationGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultPaymentOperationGuardServiceTest {

    @Test
    void shouldProceedWhenLockAcquired() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("consult:payment:guard:done:CLOSE_TRADE:ORDER-001")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                "consult:payment:guard:lock:CLOSE_TRADE:ORDER-001",
                "1",
                Duration.ofSeconds(45)
        )).thenReturn(true);

        ConsultPaymentOperationGuardService service = new ConsultPaymentOperationGuardService(providerOf(redisTemplate));

        ConsultPaymentOperationGuardService.GuardDecision decision = service.begin(
                ConsultPaymentOperationGuardService.Operation.CLOSE_TRADE,
                "order-001"
        );

        assertThat(decision.shouldProceed()).isTrue();
        assertThat(decision.shouldUseDoneState()).isFalse();
    }

    @Test
    void shouldReturnDoneWhenWaitFindsDoneMarker() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("consult:payment:guard:done:REFUND:ORDER-002"))
                .thenReturn(null, "1");
        when(valueOperations.setIfAbsent(
                "consult:payment:guard:lock:REFUND:ORDER-002",
                "1",
                Duration.ofSeconds(45)
        )).thenReturn(false);

        ConsultPaymentOperationGuardService service = new ConsultPaymentOperationGuardService(providerOf(redisTemplate));

        ConsultPaymentOperationGuardService.GuardDecision decision = service.begin(
                ConsultPaymentOperationGuardService.Operation.REFUND,
                "order-002"
        );

        assertThat(decision.shouldProceed()).isFalse();
        assertThat(decision.shouldUseDoneState()).isTrue();
    }

    @Test
    void shouldMarkDoneAndReleaseLockImmediatelyWithoutTransaction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ConsultPaymentOperationGuardService service = new ConsultPaymentOperationGuardService(providerOf(redisTemplate));

        service.markDoneAfterCommit(ConsultPaymentOperationGuardService.Operation.CALLBACK_SUCCESS, "ali-trade-003");

        verify(valueOperations).set(
                eq("consult:payment:guard:done:CALLBACK_SUCCESS:ALI-TRADE-003"),
                eq("1"),
                eq(Duration.ofMinutes(2))
        );
        verify(redisTemplate).delete("consult:payment:guard:lock:CALLBACK_SUCCESS:ALI-TRADE-003");
    }

    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
