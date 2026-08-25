package com.ice.paymentservice.Util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class MoMoUtil {

    public static String hmacSHA256(String key, String data) {
        try {
            Mac hmac256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac256.init(secretKey);
            byte[] result = hmac256.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo chữ ký HMAC-SHA256", e);
        }
    }

    /**
     * MoMo không sort alphabet toàn bộ params như VNPay — thứ tự field trong raw string
     * là CỐ ĐỊNH theo đúng tài liệu MoMo (create request), sai thứ tự là sai chữ ký.
     */
    public static String buildCreateSignatureData(String accessKey, long amount, String extraData,
                                                    String ipnUrl, String orderId, String orderInfo,
                                                    String partnerCode, String redirectUrl,
                                                    String requestId, String requestType) {
        return "accessKey=" + accessKey +
                "&amount=" + amount +
                "&extraData=" + extraData +
                "&ipnUrl=" + ipnUrl +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + partnerCode +
                "&redirectUrl=" + redirectUrl +
                "&requestId=" + requestId +
                "&requestType=" + requestType;
    }

    /** Thứ tự field IPN khác thứ tự field create request — đây là quy tắc riêng của MoMo. */
    public static String buildIpnSignatureData(String accessKey, long amount, String extraData,
                                                 String message, String orderId, String orderInfo,
                                                 String orderType, String partnerCode, String payType,
                                                 String requestId, String responseTime, String resultCode,
                                                 String transId) {
        return "accessKey=" + accessKey +
                "&amount=" + amount +
                "&extraData=" + extraData +
                "&message=" + message +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&orderType=" + orderType +
                "&partnerCode=" + partnerCode +
                "&payType=" + payType +
                "&requestId=" + requestId +
                "&responseTime=" + responseTime +
                "&resultCode=" + resultCode +
                "&transId=" + transId;
    }
}
