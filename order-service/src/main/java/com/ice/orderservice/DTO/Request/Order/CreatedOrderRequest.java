package com.ice.orderservice.DTO.Request.Order;

import com.ice.orderservice.Enum.PaymentMethod;
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
public class CreatedOrderRequest {
    @NotBlank(message = "checkout Token is not empty")
    private String checkoutToken;

    @NotBlank(message = "address Id is not empty")
    private String addressId;

    @NotNull(message = "payment method is not empty")
    private PaymentMethod paymentMethod;

    @NotBlank(message = "not is not empty")
    private String note;
}
