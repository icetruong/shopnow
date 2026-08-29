package com.ice.shippingservice.Service;

import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Exception.InvalidWebhookException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Xác thực webhook nhà vận chuyển.
 * <ul>
 *   <li>carrier.mode=mock: chỉ cần header chữ ký tồn tại (bỏ qua verify thật).</li>
 *   <li>carrier.mode=real: HMAC-SHA256(raw body, carrier.{ghn,ghtk}.webhook-secret) so hex, constant-time.</li>
 * </ul>
 *
 * <p>VERIFY: scheme ký thật của GHN/GHTK (chuỗi được ký, hex vs base64, tên header) phải đối chiếu
 * tài liệu chính thức khi tích hợp thật - phần dưới là quy ước HMAC-SHA256 hex phổ biến.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookSignatureVerifier {

    private static final String MODE_MOCK = "mock";
    private static final String HMAC_ALGO = "HmacSHA256";

    private final CarrierProperties carrierProperties;

    /** Dùng ở nhánh mock hoặc test không có raw body. */
    public void verify(String signatureHeader, String headerName) {
        if (isMock()) {
            requirePresent(signatureHeader, headerName);
            return;
        }
        throw new InvalidWebhookException(
                "carrier.mode=real cần verify chữ ký với raw body - dùng verify(sig, header, rawBody, carrier)");
    }

    public void verify(String signatureHeader, String headerName, byte[] rawBody, CarrierType carrier) {
        if (isMock()) {
            requirePresent(signatureHeader, headerName);
            return;
        }

        String secret = switch (carrier) {
            case GHN -> carrierProperties.getGhn().getWebhookSecret();
            case GHTK -> carrierProperties.getGhtk().getWebhookSecret();
        };
        if (secret == null || secret.isBlank()) {
            throw new InvalidWebhookException("Chưa cấu hình webhook-secret cho " + carrier);
        }
        requirePresent(signatureHeader, headerName);

        String expected = hmacSha256Hex(rawBody == null ? new byte[0] : rawBody, secret);
        if (!constantTimeEquals(expected, signatureHeader.trim())) {
            log.warn("Webhook {} chữ ký không khớp", carrier);
            throw new InvalidWebhookException("Chữ ký " + headerName + " không hợp lệ");
        }
    }

    private boolean isMock() {
        return MODE_MOCK.equalsIgnoreCase(carrierProperties.getMode());
    }

    private static void requirePresent(String signatureHeader, String headerName) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidWebhookException("Thiếu header " + headerName);
        }
    }

    private static String hmacSha256Hex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new InvalidWebhookException("Không tính được HMAC webhook: " + e.getMessage());
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
