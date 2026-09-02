package com.ice.reviewservice.Controller;

import com.ice.reviewservice.DTO.Request.Review.RejectReviewRequest;
import com.ice.reviewservice.DTO.Request.Review.ReplyReviewRequest;
import com.ice.reviewservice.DTO.Response.Common.ApiResponse;
import com.ice.reviewservice.DTO.Response.Review.PageReviewAdminResponse;
import com.ice.reviewservice.Enum.ReviewStatus;
import com.ice.reviewservice.Service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<Void>> replyReview(@Valid @RequestBody ReplyReviewRequest request, @PathVariable String reviewId, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        reviewService.replyReview(request, reviewId, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã trả lời đánh giá.",
                        null
                )
        );
    }

    @GetMapping("/moderation")
    public ResponseEntity<ApiResponse<PageReviewAdminResponse>> getReviewForAdmin(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ReviewStatus status
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách đánh giá thành công!",
                        reviewService.getReviewForAdmin(page, size, status)
                )
        );
    }

    @PatchMapping("/{reviewId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveReview(@PathVariable String reviewId)
    {

        reviewService.approve(reviewId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã duyệt đánh giá.",
                        null
                )
        );
    }

    @PatchMapping("/{reviewId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectReview(@PathVariable String reviewId, @Valid @RequestBody RejectReviewRequest request)
    {
        reviewService.reject(reviewId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã ẩn đánh giá.",
                        null
                )
        );
    }
}
