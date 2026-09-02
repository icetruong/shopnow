package com.ice.reviewservice.Controller;

import com.ice.reviewservice.DTO.Request.Review.CreateReviewRequest;
import com.ice.reviewservice.DTO.Request.Review.UpdateReviewRequest;
import com.ice.reviewservice.DTO.Response.Common.ApiResponse;
import com.ice.reviewservice.DTO.Response.Review.*;
import com.ice.reviewservice.Enum.ReviewStatus;
import com.ice.reviewservice.Service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
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

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<ReviewPageResponse>> getReview(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Short rating,
            @RequestParam(required = false) Boolean hasImage,
            @RequestParam(defaultValue = "newest") String sort,
            @PathVariable String productId
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách đánh giá thành công!",
                        reviewService.getReview(page, size, rating, hasImage, sort, productId)
                )
        );
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageReviewMeResponse>> getReviewMe(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "newest") String sort,
            Authentication authentication
    )
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách đánh giá thành công!",
                        reviewService.getMeReview(page, size, status, sort, userId)
                )
        );
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<ReviewPendingResponse>>> getReviewPending(Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách sản phẩm chờ đánh giá thành công!",
                        reviewService.getPendingReview(userId)
                )
        );
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> updateReview(@PathVariable String reviewId, @Valid @RequestBody UpdateReviewRequest request, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        reviewService.updateReview(reviewId, request, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã cập nhật đánh giá.",
                        null
                )
        );
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable String reviewId, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        reviewService.deleteReview(reviewId, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã xóa đánh giá.",
                        null
                )
        );
    }

    @PostMapping("/{reviewId}/helpful")
    public ResponseEntity<ApiResponse<HelpfulReviewResponse>> helpfulReview(@PathVariable String reviewId, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã đánh dấu đánh giá hữu ích.",
                        reviewService.helpfulReview(reviewId, userId)
                )
        );
    }
}
