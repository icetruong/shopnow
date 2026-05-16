package com.ice.productservice.DTO.Request.Product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequest {
    @NotBlank(message = "name must not be blank")
    @Size(min = 2, max = 255, message = "name must be between 2 and 255 characters")
    private String name;
    @NotBlank(message = "slug must not be blank")
    @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "slug must contain only lowercase letters, numbers and hyphens"
    )
    private String slug;
    private String description;
    @NotBlank(message = "categoryId must not be blank")
    private String categoryId;
    @NotNull(message = "basePrice must not be null")
    @Min(value = 1, message = "basePrice must be greater than 0")
    private Long basePrice;
    private Long salePrice;
    @NotNull(message = "isActive must not be null")
    private Boolean isActive;
    @Valid
    List<AttributeProductRequest> attributes;
    @Valid
    @NotEmpty(message = "Phải có ít nhất 1 variant")
    List<VariantProductRequest> variants;
}
