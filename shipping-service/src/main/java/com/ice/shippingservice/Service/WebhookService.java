package com.ice.shippingservice.Service;

import com.ice.shippingservice.DTO.Event.Publish.ShipmentUpdatePayload;
import com.ice.shippingservice.DTO.Webhook.CarrierWebhookEvent;
import com.ice.shippingservice.Entity.ProcessedShippingWebhook;
import com.ice.shippingservice.Entity.Shipment;
import com.ice.shippingservice.Entity.ShipmentTracking;
import com.ice.shippingservice.Repository.ProcessedShippingWebhookRepo;
import com.ice.shippingservice.Repository.ShipmentRepo;
import com.ice.shippingservice.Repository.ShipmentTrackingRepo;
import com.ice.shippingservice.Util.ShipmentStatusMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Xử lý chung cho webhook GHN & GHTK sau khi controller đã map về {@link CarrierWebhookEvent}.
 * Idempotency -> tìm shipment -> ghi timeline -> (nếu tiến hợp lệ) update status + publish.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookService {

    private final ShipmentRepo shipmentRepo;
    private final ShipmentTrackingRepo shipmentTrackingRepo;
    private final ProcessedShippingWebhookRepo processedRepo;
    private final KafkaProducerService kafkaProducerService;

    @Transactional
    public void process(CarrierWebhookEvent event) {

        // 1. IDEMPOTENCY
        if (processedRepo.existsByIdempotencyKey(event.idempotencyKey())) {
            log.info("webhook {} đã xử lý, bỏ qua", event.idempotencyKey());
            return;
        }

        // 2. tìm shipment theo trackingCode
        Optional<Shipment> opt = shipmentRepo.findByTrackingCode(event.trackingCode());
        if (opt.isEmpty()) {
            log.warn("webhook {}: không tìm thấy shipment trackingCode={}",
                    event.carrier(), event.trackingCode());
            return;                              // vẫn ack 200 để carrier ngừng retry
        }
        Shipment shipment = opt.get();

        // 3. claim idempotency key (race 2 request song song -> unique constraint)
        try {
            processedRepo.save(ProcessedShippingWebhook.builder()
                    .idempotencyKey(event.idempotencyKey())
                    .carrier(event.carrier().name())
                    .build());
        } catch (DataIntegrityViolationException dup) {
            log.info("webhook {} bị xử lý song song, bỏ qua", event.idempotencyKey());
            return;
        }

        // 4. LUÔN ghi timeline (kể cả khi status không tiến)
        shipmentTrackingRepo.save(ShipmentTracking.builder()
                .shipment(shipment)
                .status(event.status())
                .description(event.description())
                .location(event.location())
                .carrierStatus(event.carrierStatusRaw())
                .happenedAt(LocalDateTime.ofInstant(event.happenedAt(), ZoneOffset.UTC))
                .build());

        // 5. state machine: chỉ update + publish khi tiến hợp lệ
        if (!ShipmentStatusMachine.canAdvance(shipment.getStatus(), event.status())) {
            log.info("shipment {} bỏ qua transition {} -> {} (lùi / không hợp lệ)",
                    shipment.getId(), shipment.getStatus(), event.status());
            return;
        }

        shipment.setStatus(event.status());
        shipmentRepo.save(shipment);

        kafkaProducerService.publishShipmentUpdate(new ShipmentUpdatePayload(
                shipment.getOrderId().toString(),
                shipment.getId().toString(),
                shipment.getTrackingCode(),
                shipment.getCarrier(),
                shipment.getStatus().name(),
                event.description(),
                shipment.getEstimatedDate()
        ));

        log.info("shipment {} -> {} (webhook {})",
                shipment.getId(), event.status(), event.carrier());
    }
}
