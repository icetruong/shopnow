package com.ice.productservice.DTO.Request.Product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VariantProductRequest {
    private String sku;
    private String color;
    private String size;
    private Long price;
    private Integer stockQty;
}
