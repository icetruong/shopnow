package com.ice.shippingservice.Carrier.Support;

/**
 * Circuit breaker tối giản (không kéo Resilience4j) cho từng carrier.
 * CLOSED -> đủ số lần fail liên tiếp -> OPEN (chặn call) -> sau openMillis -> HALF_OPEN
 * (cho 1 call thử) -> thành công thì CLOSED, fail thì OPEN lại.
 */
public class SimpleCircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMillis;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAt = 0L;

    public SimpleCircuitBreaker(int failureThreshold, long openMillis) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMillis = Math.max(0, openMillis);
    }

    public synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= openMillis) {
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = System.currentTimeMillis();
        }
    }

    public synchronized String state() {
        return state.name();
    }
}
