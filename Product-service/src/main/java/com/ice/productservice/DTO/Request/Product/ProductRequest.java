package com.ice.productservice.DTO.Request.Product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequest {
    private String name;
    private String slug;
    private String description;
    private String categoryId;
    private Long basePrice;
    private Long salePrice;
    private Boolean isActive;
    List<AttributeProductRequest> attributes;
    List<VariantProductRequest> variants;
}
