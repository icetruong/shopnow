package com.ice.shippingservice.Controller;

import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.DTO.Webhook.CarrierWebhookEvent;
import com.ice.shippingservice.DTO.Webhook.GhtkWebhookRequest;
import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Service.WebhookService;
import com.ice.shippingservice.Service.WebhookSignatureVerifier;
import com.ice.shippingservice.Util.GhtkStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/shipping/webhook")
@RequiredArgsConstructor
@Slf4j
public class GhtkWebhookController {

    private static final String SIGNATURE_HEADER = "X-GHTK-Signature";

    /** GHTK gửi "2024-01-17 09:00:00" (không kèm timezone) - quy ước coi là UTC. */
    private static final DateTimeFormatter ACTION_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebhookService webhookService;
    private final WebhookSignatureVerifier signatureVerifier;

    @PostMapping("/ghtk")
    public ResponseEntity<ApiResponse<Void>> handle(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody GhtkWebhookRequest body) {

        signatureVerifier.verify(signature, SIGNATURE_HEADER);

        ShipmentStatus status = GhtkStatusMapper.map(body.getStatusId());
        if (status == null) {
            log.warn("GHTK status_id lạ {} cho {}, bỏ qua", body.getStatusId(), body.getLabelId());
            return ResponseEntity.ok(ApiResponse.success("ignored", null));
        }

        Instant happenedAt = LocalDateTime.parse(body.getActionTime(), ACTION_TIME_FMT)
                .toInstant(ZoneOffset.UTC);

        String idempotencyKey = "ghtk:" + body.getLabelId()
                + ":" + body.getStatusId()
                + ":" + body.getActionTime();

        webhookService.process(new CarrierWebhookEvent(
                CarrierType.GHTK,
                body.getLabelId(),
                status,
                String.valueOf(body.getStatusId()),
                body.getStatusText(),
                null,
                happenedAt,
                idempotencyKey
        ));

        return ResponseEntity.ok(ApiResponse.success("ok", null));
    }
}
