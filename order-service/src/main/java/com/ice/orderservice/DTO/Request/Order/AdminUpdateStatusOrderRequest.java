package com.ice.orderservice.DTO.Request.Order;

import com.ice.orderservice.Enum.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminUpdateStatusOrderRequest {
    @NotNull(message = "status must be not null")
    private OrderStatus status;

    @NotBlank(message = "note must be not blank")
    private String note;
}
