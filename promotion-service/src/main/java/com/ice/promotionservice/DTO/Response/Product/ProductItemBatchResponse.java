package com.ice.productservice.DTO.Response.Internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductItemBatchResponse {
    private String variantId;
    private String productId;
    private String productName;
    private String productSlug;
    private String thumbnail;
    private String sku;
    private String color;
    private String size;
    private Long price;
    private Boolean isActive;
}
