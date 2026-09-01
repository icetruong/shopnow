package com.ice.searchservice.DTO.Response.Search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SuggestProductItem {
    private String productId;
    private String name;
    private String slug;
    private String thumbnail;
    private Long salePrice;
}
