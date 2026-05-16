package com.ice.productservice.DTO.Request.Image;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ImageRequest {
    @NotBlank(message = "imageId must not be blank")
    private String imageId;
    @NotNull(message = "sortOrder must not be null")
    @Min(value = 0, message = "sortOrder must be greater than or equal to 1")
    private Integer sortOrder;
}
