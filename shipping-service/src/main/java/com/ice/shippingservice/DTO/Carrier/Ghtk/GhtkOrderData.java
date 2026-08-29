package com.ice.shippingservice.DTO.Carrier.Ghtk;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * "order" trong response create ({@code /services/shipment/order}) và tracking
 * ({@code /services/shipment/v2/{id}}). Gộp các field cả 2 nơi, phần thiếu để null.
 */
public record GhtkOrderData(
        @JsonProperty("label") String label,
        @JsonProperty("label_id") String labelId,
        @JsonProperty("partner_id") String partnerId,
        @JsonProperty("fee") Long fee,
        @JsonProperty("status_id") Integer statusId,
        /** endpoint tracking trả "status" là status_id dạng số. */
        @JsonProperty("status") Integer status,
        @JsonProperty("status_text") String statusText,
        @JsonProperty("estimated_deliver_time") String estimatedDeliverTime,
        @JsonProperty("modified") String modified
) {
    /** trackingCode = label (create) hoặc label_id (tracking). */
    public String trackingCode() {
        return label != null ? label : labelId;
    }

    /** status_id ưu tiên field status_id, fallback status. */
    public Integer effectiveStatusId() {
        return statusId != null ? statusId : status;
    }
}
