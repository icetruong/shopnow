package com.ice.shippingservice.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Config cho scheduled job retry tạo vận đơn (PendingShipmentRetryJob).
 * `interval-ms` / `initial-delay-ms` / `enabled` được đọc trực tiếp qua placeholder
 * ({@code @Scheduled}, {@code @ConditionalOnProperty}) nên không cần field ở đây.
 */
@Component
@ConfigurationProperties(prefix = "shipping.retry-job")
@Getter
@Setter
public class ShippingRetryProperties {

    /** Số lần thử tạo đơn tối đa cho 1 shipment PENDING. Chạm mốc này -> giữ PENDING, alert admin. */
    private int maxAttempts = 5;

    /** Số shipment xử lý mỗi lần job chạy (tránh 1 vòng ôm quá nhiều). */
    private int batchSize = 50;
}
