package com.ice.orderservice.DTO.Response.Order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CancelledOrderResponse {
    private String orderId;
    private String status;
}
