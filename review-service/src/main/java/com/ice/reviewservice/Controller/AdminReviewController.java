package com.ice.reviewservice.Controller;

import com.ice.reviewservice.DTO.Request.Review.ReplyReviewRequest;
import com.ice.reviewservice.DTO.Response.Common.ApiResponse;
import com.ice.reviewservice.Service.ReviewService;
import jakarta.validation.Valid;
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
}
