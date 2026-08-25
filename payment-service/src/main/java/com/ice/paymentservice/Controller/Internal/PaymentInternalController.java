package com.ice.paymentservice.Controller.Internal;

import com.ice.paymentservice.DTO.Request.Payment.CreatePaymentRequest;
import com.ice.paymentservice.Service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/payments")
public class PaymentInternalController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public ResponseEntity<Object> createPayment(@RequestBody CreatePaymentRequest request)
    {
        return ResponseEntity.ok(paymentService.createPayment(request));
    }
}
