package com.ice.shippingservice.DTO.Webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body GHTK gửi tới POST /shipping/webhook/ghtk (JSON snake_case). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GhtkWebhookRequest {

    @JsonProperty("partner_id")
    private String partnerId;

    /** = trackingCode bên mình (spec: order.label -> trackingCode). */
    @JsonProperty("label_id")
    private String labelId;

    @JsonProperty("status_id")
    private Integer statusId;

    @JsonProperty("status_text")
    private String statusText;

    /** "2024-01-17 09:00:00" - KHÔNG phải ISO, giữ String rồi parse tay. */
    @JsonProperty("action_time")
    private String actionTime;
}
