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
public class PageReviewMeResponse {
    private List<ReviewMeResponse> content;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
