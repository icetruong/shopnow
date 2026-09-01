package com.ice.searchservice.DTO.Response.Search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SuggestionItem {
    private String text;
    private String type;
}
