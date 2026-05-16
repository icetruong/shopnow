package com.ice.productservice.DTO.Request.Product;


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
public class CreateVariantProductRequest {
    @NotBlank(message = "SKU không được trống")
    private String sku;

    @NotBlank(message = "Màu sắc không được trống")
    private String color;

    @NotBlank(message = "Kích thước không được trống")
    private String size;
    @NotNull(message = "Giá không được trống")
    @Min(value = 1, message = "Giá phải > 0")
    private Long price;

    @NotNull(message = "Số lượng không được trống")
    @Min(value = 0, message = "Số lượng phải >= 0")
    private Integer stockQty;

    @NotBlank(message = "imageUrl không được trống")
    private String imageUrl;
}
