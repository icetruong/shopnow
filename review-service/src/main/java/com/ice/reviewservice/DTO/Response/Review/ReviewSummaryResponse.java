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
public class ReviewSummaryResponse {
    private Double avgRating;
    private Long totalReviews;
    private Map<String, Integer> distribution;
    private Long withImageCount;
}
