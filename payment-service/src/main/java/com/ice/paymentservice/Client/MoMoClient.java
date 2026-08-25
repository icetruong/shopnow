package com.ice.paymentservice.Client;

import com.ice.paymentservice.DTO.Request.Payment.MoMoCreatePaymentRequest;
import com.ice.paymentservice.DTO.Response.Payment.MoMoCreatePaymentResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MoMoClient {

    private final RestClient restClient = RestClient.create();

    public MoMoCreatePaymentResponse createPayment(String endpoint, MoMoCreatePaymentRequest request) {
        return restClient.post()
                .uri(endpoint)
                .body(request)
                .retrieve()
                .body(MoMoCreatePaymentResponse.class);
    }
}
