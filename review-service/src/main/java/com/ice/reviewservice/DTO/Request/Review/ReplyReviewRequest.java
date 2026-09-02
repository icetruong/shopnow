package com.ice.reviewservice.DTO.Request.Review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReplyReviewRequest {
    @NotBlank(message = "content must be not blank")
    @Size(max = 1000, message = "content max is 1000 character")
    private String content;
}
