package com.ice.promotionservice.DTO.Request.FlashSale;

import jakarta.validation.constraints.Min;
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
public class FlashSaleRollbackRequest {
    @NotBlank
    private String flashSaleId;

    @NotBlank
    private String variantId;

    @NotBlank
    private String userId;

    @NotBlank
    private String orderId;

    @NotNull
    @Min(1)
    private Integer qty;
}
