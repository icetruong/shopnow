package com.ice.paymentservice.Controller.Internal;

import com.ice.paymentservice.DTO.Request.Payment.CreatePaymentRequest;
import com.ice.paymentservice.DTO.Response.Common.ApiResponse;
import com.ice.paymentservice.DTO.Response.Payment.ConfirmCodResponse;
import com.ice.paymentservice.DTO.Response.Payment.PaymentInternalResponse;
import com.ice.paymentservice.DTO.Response.Payment.PaymentResponse;
import com.ice.paymentservice.Service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/payments")
public class PaymentInternalController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public ResponseEntity<Object> createPayment(@RequestBody CreatePaymentRequest request, HttpServletRequest httpRequest)
    {
        return ResponseEntity.ok(paymentService.createPayment(request, httpRequest));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentInternalResponse> getPayment(@PathVariable String orderId)
    {
        return ResponseEntity.ok(
                        paymentService.getPaymentInternal(orderId)
        );
    }

    @PatchMapping("/{paymentId}/confirm-cod")
    public ResponseEntity<ConfirmCodResponse> confirmCod(@PathVariable String paymentId)
    {
        return ResponseEntity.ok(
                paymentService.confirmCodPayment(paymentId)
        );
    }
}
