package com.ice.reviewservice.DTO.Response.Review;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private String reviewId;
    private String userName;
    private String userAvatar;
    private Integer rating;
    private String comment;
    private List<String> images;
    private String variantInfo;
    private Boolean isVerifiedPurchase;
    private Integer helpfulCount;
    private ShopReplyResponse shopReply;
    private Instant createdAt;
}
