package com.ice.reviewservice.Controller;

import com.ice.reviewservice.DTO.Request.Review.CreateReviewRequest;
import com.ice.reviewservice.DTO.Response.Common.ApiResponse;
import com.ice.reviewservice.DTO.Response.Review.CreateReviewResponse;
import com.ice.reviewservice.Service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateReviewResponse>> createReview(@Valid @RequestBody CreateReviewRequest request, Authentication authentication)
    {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Cảm ơn bạn đã đánh giá!",
                                reviewService.createReview(userId, request)
                        )
                );
    }
}
