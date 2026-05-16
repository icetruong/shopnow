package com.ice.productservice.DTO.Response.Product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponse {
    private String productId;
    private String name;
    private String slug;
    private String thumbnail;
    private Long basePrice;
    private Long salePrice;
    private Integer discountPct;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer soldCount;
    private String categoryId;
    private String categoryName;
    private Boolean isActive;
}
