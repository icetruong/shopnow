package com.ice.productservice.DTO.Response.Image;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ImageUploadResponse {
    private String imageId;
    private String url;
    private String altText;
    private Integer sortOrder;
    private Boolean isPrimary;
}
