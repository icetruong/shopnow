package com.ice.reviewservice.DTO.Response.Review;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewSummaryResponse {
    private String productId;
    private Double avgRating;
    private Long totalReviews;
    Map<String, Integer> distribution;
}
