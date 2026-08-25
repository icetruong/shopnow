package com.ice.paymentservice.Controller;

import com.ice.paymentservice.DTO.Response.Common.ApiResponse;
import com.ice.paymentservice.DTO.Response.Payment.VNPayIpnResponse;
import com.ice.paymentservice.DTO.Response.Payment.VNPayReturnResponse;
import com.ice.paymentservice.Service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/vnpay")
public class VNPayCallbackController {

    private final PaymentService paymentService;

    @GetMapping("/return")
    public ResponseEntity<ApiResponse<VNPayReturnResponse>> vnPayReturn(@RequestParam Map<String, String> params) {
        VNPayReturnResponse result = paymentService.handleVnPayReturn(params);
        return ResponseEntity.ok(ApiResponse.success("Kiểm tra kết quả thanh toán", result));
    }

    @GetMapping("/ipn")
    public ResponseEntity<VNPayIpnResponse> vnPayIpn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.handleVnPayIpn(params));
    }
}
