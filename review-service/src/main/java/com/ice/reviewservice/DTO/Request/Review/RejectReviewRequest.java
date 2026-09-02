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
public class RejectReviewRequest {

    @NotBlank(message = "reason must be not blank")
    @Size(max = 1000, message = "reason max is 1000 character")
    private String reason;
}
