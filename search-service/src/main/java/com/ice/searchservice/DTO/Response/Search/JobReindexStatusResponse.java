package com.ice.searchservice.DTO.Response.Search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobReindexStatusResponse {
    private String jobId;
    private String status;
    private Long total;
    private Long processed;
    private Long progress;
}
