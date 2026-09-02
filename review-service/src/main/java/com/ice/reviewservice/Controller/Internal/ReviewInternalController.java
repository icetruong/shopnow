package com.ice.reviewservice.Controller.Internal;

import com.ice.reviewservice.DTO.Response.Review.ProductReviewSummaryResponse;
import com.ice.reviewservice.Service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/reviews")
public class ReviewInternalController {

    private final ReviewService reviewService;

    @GetMapping("/product/{productId}/summary")
    public ResponseEntity<ProductReviewSummaryResponse> getProductSummary(@PathVariable String productId)
    {
        return ResponseEntity.ok(
                reviewService.getProductSummary(productId)
        );
    }
}
