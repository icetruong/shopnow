package com.ice.productservice.DTO.Response.Internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductInternalResponse {
    private String productId;
    private String name;
    private Boolean isActive;
    private String categoryId;
}
