package com.ice.productservice.DTO.Response.Search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SearchProductResponse {
    private String productId;
    private String name;
    private String slug;
    private String thumbnail;
    private Long basePrice;
    private Long salePrice;
    private BigDecimal rating;
    private Integer soldCount;
    private Map<String, List<String>> highlight;
}
