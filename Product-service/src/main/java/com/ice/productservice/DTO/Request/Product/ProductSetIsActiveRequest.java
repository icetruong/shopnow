package com.ice.productservice.DTO.Request.Product;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductSetIsActiveRequest {
    @NotNull(message = "isActive must not be null")
    private Boolean isActive;
}
