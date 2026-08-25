package com.ice.paymentservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReconciliationResponse {
    private LocalDate date;
    private String method;
    private int totalInDb;
    private long totalAmount;
    private int matched;
    private int mismatched;
    private List<ReconciliationMismatchDetail> mismatchDetails;
}
