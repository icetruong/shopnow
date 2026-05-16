package com.ice.productservice.DTO.Request.Internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductRatingInternalRequest {
    private String productId;
    private BigDecimal newRating;
    private Long totalReviews;
    private BigDecimal avgRating;
}
