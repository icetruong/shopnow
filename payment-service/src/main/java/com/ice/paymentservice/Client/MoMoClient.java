package com.ice.paymentservice.Client;

import com.ice.paymentservice.DTO.Request.Payment.MoMoCreatePaymentRequest;
import com.ice.paymentservice.DTO.Request.Payment.MoMoQueryRequest;
import com.ice.paymentservice.DTO.Request.Payment.MoMoRefundRequest;
import com.ice.paymentservice.DTO.Response.Payment.MoMoCreatePaymentResponse;
import com.ice.paymentservice.DTO.Response.Payment.MoMoQueryResponse;
import com.ice.paymentservice.DTO.Response.Payment.MoMoRefundResponse;
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

    public MoMoRefundResponse refund(String endpoint, MoMoRefundRequest request) {
        return restClient.post()
                .uri(endpoint)
                .body(request)
                .retrieve()
                .body(MoMoRefundResponse.class);
    }

    public MoMoQueryResponse queryStatus(String endpoint, MoMoQueryRequest request) {
        return restClient.post()
                .uri(endpoint)
                .body(request)
                .retrieve()
                .body(MoMoQueryResponse.class);
    }
}
