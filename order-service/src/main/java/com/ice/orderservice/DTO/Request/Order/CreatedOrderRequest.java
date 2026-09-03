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
    @NotBlank(message = "checkoutToken không được để trống")
    private String checkoutToken;

    @NotBlank(message = "addressId không được để trống")
    private String addressId;

    @NotNull(message = "paymentMethod không được để trống")
    private PaymentMethod paymentMethod;

    // Ghi chú của khách — không bắt buộc.
    private String note;
}
