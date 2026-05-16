package com.ice.productservice.DTO.Request.Category;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "name must not be blank")
    @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "slug must not be blank")
    @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "slug must contain only lowercase letters, numbers and hyphens"
    )
    private String slug;
    private String parentId;
    private String imageUrl;

    @NotNull(message = "sortOrder must not be null")
    @Min(value = 0, message = "sortOrder must be greater than or equal to 0")
    private Integer sortOrder;
}
