package com.ice.paymentservice.Controller;

import com.ice.paymentservice.Service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/stripe")
public class StripeCallbackController {

    private final PaymentService paymentService;

    /**
     * Payload phải nhận dạng String thô (không parse JSON) vì Stripe verify chữ ký
     * trên đúng raw bytes gửi đi — parse rồi build lại JSON sẽ làm sai signature.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> stripeWebhook(@RequestBody String payload,
                                               @RequestHeader("Stripe-Signature") String sigHeader) {
        paymentService.handleStripeWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }
}
