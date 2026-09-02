package com.ice.reviewservice.DTO.Request.Review;

import com.ice.reviewservice.Enum.ReviewReportReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportReviewRequest {
    @NotNull(message = "reason must be not null (SPAM | OFFENSIVE | FAKE | IRRELEVANT)")
    private ReviewReportReason reason;
}
