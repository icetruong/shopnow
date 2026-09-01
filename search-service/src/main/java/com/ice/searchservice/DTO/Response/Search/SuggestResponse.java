package com.ice.searchservice.DTO.Response.Search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SuggestResponse {
    private List<SuggestionItem> suggestions;
    private List<SuggestProductItem> products;
}
