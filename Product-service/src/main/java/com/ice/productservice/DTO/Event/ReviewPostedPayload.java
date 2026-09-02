package com.ice.productservice.DTO.Event;

import com.ice.productservice.Enum.ReviewPostAction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReviewPostedPayload {
    private String reviewId;
    private String productId;
    private String userId;
    private Double rating;
    private Double avgRating;
    private Long totalReviews;
    private ReviewPostAction action;
}
