package com.ice.searchservice.Controller.Internal;

import com.ice.searchservice.DTO.Response.Common.ApiResponse;
import com.ice.searchservice.DTO.Response.Search.JobReindexResponse;
import com.ice.searchservice.DTO.Response.Search.JobReindexStatusResponse;
import com.ice.searchservice.Service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/search")
public class SearchInternalController {
    private final SearchService searchService;

    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<JobReindexResponse>> reindex()
    {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success(
                        "Đã bắt đầu reindex. Quá trình chạy nền.",
                        searchService.startReindex()
                )
        );
    }

    @GetMapping("/reindex/{jobId}/status")
    public ResponseEntity<ApiResponse<JobReindexStatusResponse>> reindexStatus(@PathVariable String jobId)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "retrieved successfully",
                        searchService.getReindex(jobId)
                )
        );
    }
}
