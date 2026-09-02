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
public class ReviewPendingResponse {
    private String orderId;
    private String productId;
    private String variantId;
    private String productName;
    private String thumbnail;
    private String variantInfo;
    private Instant deliveredAt;
}
