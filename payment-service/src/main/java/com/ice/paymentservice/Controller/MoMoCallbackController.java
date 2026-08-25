package com.ice.paymentservice.Controller;

import com.ice.paymentservice.DTO.Request.Payment.MoMoIpnRequest;
import com.ice.paymentservice.Service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/momo")
public class MoMoCallbackController {

    private final PaymentService paymentService;

    @PostMapping("/ipn")
    public ResponseEntity<Void> moMoIpn(@RequestBody MoMoIpnRequest ipn) {
        paymentService.handleMoMoIpn(ipn);
        return ResponseEntity.noContent().build();
    }
}
