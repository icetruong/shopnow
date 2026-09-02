package com.ice.reviewservice.DTO.Request.Review;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {
    @NotBlank(message = "orderId must be not blank")
    private String orderId;

    @NotBlank(message = "productId must be not blank")
    private String productId;

    @NotBlank(message = "variantId must be not blank")
    private String variantId;

    @NotNull(message = "rating must be not null")
    @Min(value = 1, message = "rating must be >= 1")
    @Max(value = 5, message = "rating must be <= 5")
    private Integer rating;

    @Size(max = 1000, message = "comment max is 1000 character")
    private String comment;

    @Size(max = 5, message = "max 5 image")
    private List<String> images;
}
