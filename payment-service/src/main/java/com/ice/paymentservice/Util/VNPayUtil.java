package com.ice.paymentservice.Util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public class VNPayUtil {

    public static String hmacSHA512(String key, String data)
    {
        try
        {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Không thể tạo chữ ký HMAC-SHA512", e);
        }
    }

    public static String buildHashData(Map<String, String> params)
    {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            hashData.append(entry.getKey())
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
            hashData.append('&');
        }
        hashData.setLength(hashData.length() - 1); // bỏ dấu & cuối cùng
        return hashData.toString();
    }

    public static String getIpAddress(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    public static String buildPaymentUrl(String payUrl, String hashSecret, Map<String, String> params) {
        String hashData = buildHashData(params);
        String secureHash = hmacSHA512(hashSecret, hashData);
        return payUrl + "?" + hashData + "&vnp_SecureHash=" + secureHash;
    }

    public static boolean isValidSignature(Map<String, String> params, String hashSecret) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        Map<String, String> filtered = new TreeMap<>(params);
        filtered.remove("vnp_SecureHash");
        filtered.remove("vnp_SecureHashType");

        String hashData = buildHashData(filtered);
        String computedHash = hmacSHA512(hashSecret, hashData);

        return computedHash.equalsIgnoreCase(receivedHash);
    }
}
