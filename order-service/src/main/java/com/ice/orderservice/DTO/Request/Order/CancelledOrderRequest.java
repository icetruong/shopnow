package com.ice.orderservice.DTO.Request.Order;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CancelledOrderRequest {
    @NotBlank(message = "reason must not blank")
    private String reason;
}
