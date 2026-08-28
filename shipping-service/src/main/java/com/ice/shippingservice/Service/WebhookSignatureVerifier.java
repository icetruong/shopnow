package com.ice.shippingservice.Service;

import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.Exception.InvalidWebhookException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Xác thực webhook nhà vận chuyển.
 * - carrier.mode=mock: chỉ cần header chữ ký tồn tại (bỏ qua verify thật).
 * - carrier.mode=real: TODO so HMAC với carrier.{ghn,ghtk}.webhook-secret.
 */
@Service
@RequiredArgsConstructor
public class WebhookSignatureVerifier {

    private static final String MODE_MOCK = "mock";

    private final CarrierProperties carrierProperties;

    public void verify(String signatureHeader, String headerName) {
        if (MODE_MOCK.equalsIgnoreCase(carrierProperties.getMode())) {
            if (signatureHeader == null || signatureHeader.isBlank()) {
                throw new InvalidWebhookException("Thiếu header " + headerName);
            }
            return;
        }
        throw new InvalidWebhookException("Chưa hỗ trợ verify chữ ký thật (carrier.mode=real)");
    }
}
