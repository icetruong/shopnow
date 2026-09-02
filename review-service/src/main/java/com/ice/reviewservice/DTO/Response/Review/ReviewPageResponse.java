package com.ice.reviewservice.DTO.Response.Review;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewPageResponse {
    List<ReviewResponse> content;
    private Integer page;
    private Long totalElements;
    private ReviewSummaryResponse summary;
}
