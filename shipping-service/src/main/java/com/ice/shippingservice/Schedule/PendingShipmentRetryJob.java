package com.ice.shippingservice.Schedule;

import com.ice.shippingservice.Config.ShippingRetryProperties;
import com.ice.shippingservice.Entity.Shipment;
import com.ice.shippingservice.Exception.OrderServiceUnavailableException;
import com.ice.shippingservice.Repository.ShipmentRepo;
import com.ice.shippingservice.Service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * PHẦN 3 - "Retry khi API nhà vận chuyển lỗi": mỗi {@code interval-ms} quét các shipment còn
 * PENDING (map địa chỉ fail / carrier API lỗi) và chạy lại "Flow tạo vận đơn" từ bước resolve.
 *
 * <p>Mỗi shipment được xử lý trong 1 transaction riêng qua {@link ShippingService#retryPending(UUID)}
 * nên 1 cái fail không kéo theo cái khác. Tắt job: {@code shipping.retry-job.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "shipping.retry-job.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PendingShipmentRetryJob {

    private final ShippingService shippingService;
    private final ShipmentRepo shipmentRepo;
    private final ShippingRetryProperties shippingRetryProperties;

    @Scheduled(
            fixedDelayString = "${shipping.retry-job.interval-ms}",
            initialDelayString = "${shipping.retry-job.initial-delay-ms}"
    )
    public void run() {
        int maxAttempts = shippingRetryProperties.getMaxAttempts();

        List<UUID> ids = shipmentRepo.findPendingIdsForRetry(
                maxAttempts,
                PageRequest.of(0, shippingRetryProperties.getBatchSize())
        );
        if (ids.isEmpty()) {
            return;
        }
        log.info("Retry job: {} shipment PENDING cần thử lại", ids.size());

        int ok = 0, stillPending = 0, exhausted = 0, failed = 0;
        for (UUID id : ids) {
            try {
                Shipment s = shippingService.retryPending(id);
                if (s == null) {
                    continue;   // không còn PENDING (admin/endpoint xử lý xen giữa)
                }
                switch (s.getStatus()) {
                    case READY_TO_PICK -> ok++;
                    case PENDING -> {
                        stillPending++;
                        if (s.getRetryCount() >= maxAttempts) {
                            exhausted++;
                            // TODO: đẩy alert thật (Kafka topic alert / Slack / email) - giống chỗ alert admin hiện có
                            log.error("ALERT: shipment {} đã retry {} lần vẫn PENDING (reason={}), cần xử lý tay",
                                    id, s.getRetryCount(), s.getFailureReason());
                        } else {
                            log.warn("Retry job: shipment {} vẫn PENDING (reason={}), retryCount={}",
                                    id, s.getFailureReason(), s.getRetryCount());
                        }
                    }
                    default -> { /* trạng thái khác - bỏ qua */ }
                }
            } catch (OrderServiceUnavailableException e) {
                failed++;
                log.warn("Retry job: shipment {} - order-service không sẵn sàng, để lần sau: {}", id, e.getMessage());
            } catch (Exception e) {
                failed++;
                log.error("Retry job: shipment {} lỗi bất ngờ khi retry", id, e);
            }
        }

        log.info("Retry job xong: ok={}, vẫnPending={}, hếtLượt={}, lỗi={}", ok, stillPending, exhausted, failed);
    }
}
