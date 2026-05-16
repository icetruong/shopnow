package com.ice.productservice.DTO.Request.Product;

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
public class VariantProductRequest {
    @NotBlank(message = "sku must not be blank")
    private String sku;

    @NotBlank(message = "color must not be blank")
    private String color;

    @NotBlank(message = "size must not be blank")
    private String size;
    @NotNull(message = "price must not be null")
    @Min(value = 1, message = "price must be greater than 0")
    private Long price;

    @NotNull(message = "stockQty must not be null")
    @Min(value = 0, message = "stockQty must be greater than or equal to 0")
    private Integer stockQty;
}
