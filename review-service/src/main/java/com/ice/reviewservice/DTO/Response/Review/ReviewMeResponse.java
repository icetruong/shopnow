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
public class ReviewMeResponse {
    private String reviewId;
    private String productId;
    private String variantId;
    private String variantInfo;
    private Short rating;
    private String comment;
    private List<String> images;
    private String status;
    private String flaggedReason;
    private Integer helpfulCount;
    private ShopReplyResponse shopReply;
    private boolean editable;
    private Instant createdAt;
    private Instant updatedAt;
}
