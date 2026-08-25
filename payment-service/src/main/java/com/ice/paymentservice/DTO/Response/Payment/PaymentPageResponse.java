package com.ice.paymentservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentPageResponse {
    private List<PaymentDetailResponse> content;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
