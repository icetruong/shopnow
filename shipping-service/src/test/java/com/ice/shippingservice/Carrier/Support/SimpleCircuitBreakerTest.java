package com.ice.shippingservice.Carrier.Support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleCircuitBreakerTest {

    @Test
    void opensAfterThresholdConsecutiveFailures() {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker(3, 10_000);

        assertThat(cb.allowRequest()).isTrue();
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.allowRequest()).isTrue();   // 2 fail < threshold
        cb.recordFailure();                       // fail thứ 3 -> OPEN

        assertThat(cb.allowRequest()).isFalse();
        assertThat(cb.state()).isEqualTo("OPEN");
    }

    @Test
    void halfOpenAfterCooldownThenClosesOnSuccess() throws InterruptedException {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker(1, 50);
        cb.recordFailure();                        // -> OPEN
        assertThat(cb.allowRequest()).isFalse();

        Thread.sleep(70);
        assertThat(cb.allowRequest()).isTrue();    // -> HALF_OPEN cho 1 call thử
        cb.recordSuccess();
        assertThat(cb.state()).isEqualTo("CLOSED");
    }

    @Test
    void successResetsFailureCount() {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker(2, 1_000);
        cb.recordFailure();
        cb.recordSuccess();
        cb.recordFailure();
        assertThat(cb.allowRequest()).isTrue();    // chưa đủ 2 fail liên tiếp
    }
}
