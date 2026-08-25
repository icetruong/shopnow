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

    /**
     * Refund/querydr KHÔNG dùng format key=value&... như payment URL — VNPay yêu cầu raw string
     * nối các field bằng dấu "|" theo đúng thứ tự cố định trong tài liệu, không sort, không encode.
     */
    public static String buildRefundHashData(String requestId, String version, String command, String tmnCode,
                                               String transactionType, String txnRef, long amount,
                                               String transactionNo, String transactionDate, String createBy,
                                               String createDate, String ipAddr, String orderInfo) {
        return String.join("|",
                requestId, version, command, tmnCode, transactionType, txnRef,
                String.valueOf(amount), transactionNo, transactionDate, createBy,
                createDate, ipAddr, orderInfo);
    }

    /** Thứ tự field querydr khác refund — cũng nối bằng "|", cũng không có vnp_TransactionType. */
    public static String buildQueryDrHashData(String requestId, String version, String command, String tmnCode,
                                                String txnRef, String transactionDate, String createDate,
                                                String ipAddr, String orderInfo) {
        return String.join("|",
                requestId, version, command, tmnCode, txnRef,
                transactionDate, createDate, ipAddr, orderInfo);
    }
}
