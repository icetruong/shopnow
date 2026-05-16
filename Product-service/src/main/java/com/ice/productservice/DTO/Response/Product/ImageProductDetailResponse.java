package com.ice.productservice.DTO.Response.Product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ImageProductDetailResponse {
    private String imageId;
    private String url;
    private String altText;
    private Integer sortOrder;
    private Boolean isPrimary;
}
