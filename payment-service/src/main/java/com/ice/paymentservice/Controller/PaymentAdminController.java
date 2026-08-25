package com.ice.paymentservice.Controller;

import com.ice.paymentservice.DTO.Response.Common.ApiResponse;
import com.ice.paymentservice.DTO.Response.Payment.PaymentPageResponse;
import com.ice.paymentservice.DTO.Response.Payment.ReconciliationResponse;
import com.ice.paymentservice.Enum.PaymentMethod;
import com.ice.paymentservice.Enum.PaymentStatus;
import com.ice.paymentservice.Service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payments")
public class PaymentAdminController {
    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaymentPageResponse>> getPaymentDetail(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách thanh toán thành công",
                        paymentService.getPaymentDetail(page, size, status, method, startDate, endDate)
                )
        );
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> getReconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam PaymentMethod method,
            HttpServletRequest httpRequest
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đối soát giao dịch thành công",
                        paymentService.getReconciliation(date, method, httpRequest)
                )
        );
    }
}
