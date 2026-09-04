package com.ice.promotionservice.DTO.Request.FlashSale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateAdminFlashSaleItemRequest {

    @NotBlank
    private String productId;

    @NotBlank
    private String variantId;

    @NotNull
    @Positive
    private Long flashPrice;

    @NotNull
    @Positive
    private Integer totalQty;

    @NotNull
    @Positive
    private Integer limitPerUser;
}
