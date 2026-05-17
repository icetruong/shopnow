package com.ice.productservice.DTO.Response.Search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AggregationsResponse {
    private List<CategoryAggregation> categories;
    private List<PriceRangeAggregation> priceRanges;
    private List<ColorAggregation> colors;
    private List<String> sizes;
}
