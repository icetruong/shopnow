package com.ice.shippingservice.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body cho POST /internal/shipments.
 * userId truyền kèm vì GET /internal/orders/{orderId} của order-service không trả userId
 * (caller - admin UI / retry job - luôn có sẵn userId).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateShipmentRequest {

    @NotBlank(message = "orderId không được để trống")
    private String orderId;

    @NotBlank(message = "userId không được để trống")
    private String userId;
}
