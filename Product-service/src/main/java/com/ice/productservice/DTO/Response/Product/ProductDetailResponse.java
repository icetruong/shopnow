package com.ice.productservice.DTO.Response.Product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductDetailResponse {
    private String productId;
    private String name;
    private String slug;
    private String description;
    private Long basePrice;
    private Long salePrice;
    private Integer discountPct;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer soldCount;
    private String categoryId;
    private String categoryName;
    private Boolean isActive;
    List<ImageProductDetailResponse> images;
    List<AttributeProductDetailResponse> attributes;
    List<VariantProductDetailResponse> variants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
