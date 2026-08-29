package com.ice.shippingservice.Controller;

import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.DTO.Webhook.CarrierWebhookEvent;
import com.ice.shippingservice.DTO.Webhook.GhnWebhookRequest;
import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Exception.InvalidWebhookException;
import com.ice.shippingservice.Service.WebhookService;
import com.ice.shippingservice.Service.WebhookSignatureVerifier;
import com.ice.shippingservice.Util.GhnStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/shipping/webhook")
@RequiredArgsConstructor
@Slf4j
public class GhnWebhookController {

    private static final String SIGNATURE_HEADER = "X-GHN-Signature";

    private final WebhookService webhookService;
    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    @PostMapping("/ghn")
    public ResponseEntity<ApiResponse<Void>> handle(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] rawBody) {

        signatureVerifier.verify(signature, SIGNATURE_HEADER, rawBody, CarrierType.GHN);

        GhnWebhookRequest body;
        try {
            body = objectMapper.readValue(rawBody, GhnWebhookRequest.class);
        } catch (RuntimeException e) {
            throw new InvalidWebhookException("Body webhook GHN không đọc được: " + e.getMessage());
        }

        ShipmentStatus status = GhnStatusMapper.map(body.getStatus());
        if (status == null) {
            log.warn("GHN status lạ '{}' cho {}, bỏ qua", body.getStatus(), body.getOrderCode());
            return ResponseEntity.ok(ApiResponse.success("ignored", null));
        }

        String idempotencyKey = "ghn:" + body.getOrderCode()
                + ":" + body.getStatus()
                + ":" + body.getTime();

        webhookService.process(new CarrierWebhookEvent(
                CarrierType.GHN,
                body.getOrderCode(),
                status,
                body.getStatus(),
                body.getDescription(),
                body.getWarehouse(),
                body.getTime(),
                idempotencyKey
        ));

        return ResponseEntity.ok(ApiResponse.success("ok", null));
    }
}
