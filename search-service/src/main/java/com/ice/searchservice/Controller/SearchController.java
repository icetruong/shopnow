package com.ice.searchservice.Controller;

import com.ice.searchservice.DTO.Response.Common.ApiResponse;
import com.ice.searchservice.DTO.Response.Search.PageSearchProductResponse;
import com.ice.searchservice.DTO.Response.Search.SuggestResponse;
import com.ice.searchservice.Service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    // GET /api/v1/search — full-text + filter + sort + aggregation
    @GetMapping
    public ResponseEntity<ApiResponse<PageSearchProductResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) List<String> colors,
            @RequestParam(required = false) List<String> sizes,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "relevance") String sort
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        searchService.search(q, page, size, categoryId, minPrice, maxPrice, colors, sizes, minRating, sort)
                )
        );
    }

    // GET /api/v1/search/suggest — autocomplete khi user đang gõ
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<SuggestResponse>> suggest(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") Integer size
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        searchService.suggest(q, size)
                )
        );
    }
}
