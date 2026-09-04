package com.ice.productservice.DTO.Response.Internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductBatchResponse {
    List<ProductItemBatchResponse> variants;
}
