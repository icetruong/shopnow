package com.ice.productservice.DTO.Request.Product;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AttributeProductRequest {
    @NotBlank(message = "attribute name must not be blank")
    private String name;

    @NotBlank(message = "attribute value must not be blank")
    private String value;
}
