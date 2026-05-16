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
public class UpdateVariantProductRequest {
    @NotNull(message = "price must not be null")
    @Min(value = 1, message = "price must be greater than 0")
    private Long price;

    @NotBlank(message = "imageUrl must not be blank")
    private String imageUrl;
}
