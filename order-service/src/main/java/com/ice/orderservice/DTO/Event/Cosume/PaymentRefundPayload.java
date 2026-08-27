package com.ice.orderservice.DTO.Event.Cosume;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRefundPayload {
    private String orderId;
    private String refundId;
    private Long amount;
    private Instant refundedAt;
}
