package com.ice.productservice.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CategoryResponse {
    private String categoryId;
    private String name;
    private String slug;
    private String imageUrl;
    List<CategoryResponse> children;
}
