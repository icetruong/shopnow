package com.ice.shippingservice.Carrier.Support;

import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.Exception.CarrierApiException;
import com.ice.shippingservice.Exception.CarrierCannotCancelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Bọc mọi call sang GHN/GHTK với: circuit breaker (theo key carrier) + retry backoff cố định.
 * Thay cho Resilience4j để không phụ thuộc autoconfig trên Spring Boot 4.
 *
 * <p>Quy ước: {@link CarrierCannotCancelException} là "carrier trả lời hợp lệ" (đơn đã lấy hàng),
 * KHÔNG retry, KHÔNG tính là lỗi hạ tầng. Mọi {@link RuntimeException} khác -> retry -> hết lượt
 * gói thành {@link CarrierApiException}.
 */
@Component
@Slf4j
public class CarrierCallExecutor {

    private final int maxAttempts;
    private final long backoffMs;
    private final int cbFailureThreshold;
    private final long cbOpenMs;
    private final Map<String, SimpleCircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CarrierCallExecutor(CarrierProperties props) {
        CarrierProperties.Resilience r = props.getResilience();
        this.maxAttempts = Math.max(1, r.getMaxAttempts());
        this.backoffMs = Math.max(0, r.getBackoffMs());
        this.cbFailureThreshold = r.getCbFailureThreshold();
        this.cbOpenMs = r.getCbOpenMs();
    }

    public <T> T call(String breakerKey, String opName, Supplier<T> op) {
        SimpleCircuitBreaker cb = breakers.computeIfAbsent(
                breakerKey, k -> new SimpleCircuitBreaker(cbFailureThreshold, cbOpenMs));

        if (!cb.allowRequest()) {
            throw new CarrierApiException(
                    "Circuit breaker đang OPEN cho " + breakerKey + " - tạm ngừng gọi " + opName);
        }

        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = op.get();
                cb.recordSuccess();
                return result;
            } catch (CarrierCannotCancelException e) {
                cb.recordSuccess();          // carrier phản hồi bình thường, chỉ là không cho huỷ
                throw e;
            } catch (RuntimeException e) {
                last = e;
                cb.recordFailure();
                log.warn("{} {} lần {}/{} lỗi: {}", breakerKey, opName, attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(backoffMs);
                }
            }
        }
        throw new CarrierApiException(
                opName + " thất bại sau " + maxAttempts + " lần với " + breakerKey, last);
    }

    public void run(String breakerKey, String opName, Runnable op) {
        call(breakerKey, opName, () -> {
            op.run();
            return null;
        });
    }

    public String circuitState(String breakerKey) {
        SimpleCircuitBreaker cb = breakers.get(breakerKey);
        return cb == null ? "CLOSED" : cb.state();
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CarrierApiException("Bị ngắt khi chờ retry carrier", ie);
        }
    }
}
