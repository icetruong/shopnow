package com.ice.reviewservice.DTO.Response.Review;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAdminResponse {
    private String reviewId;
    private String productId;
    private String userName;
    private Short rating;
    private String comment;
    private String flaggedReason;
    private Integer reportCount;
    private Instant createdAt;
}
