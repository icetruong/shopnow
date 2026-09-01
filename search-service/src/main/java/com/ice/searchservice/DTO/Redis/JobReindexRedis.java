package com.ice.searchservice.DTO.Redis;

import com.ice.searchservice.Enum.JobReindexStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobReindexRedis {
    private JobReindexStatus status;
    private Long total;
    private Long processed;
}
