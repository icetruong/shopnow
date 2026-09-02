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
public class PageReviewAdminResponse {
    private List<ReviewAdminResponse> content;
    private Integer page;
    private Integer size;
    private Long totalElement;
    private Integer totalPages;
}
