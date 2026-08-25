package com.ice.paymentservice.Client;

import com.ice.paymentservice.DTO.Request.Payment.VNPayQueryDrRequest;
import com.ice.paymentservice.DTO.Request.Payment.VNPayRefundRequest;
import com.ice.paymentservice.DTO.Response.Payment.VNPayTransactionResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class VNPayClient {

    private final RestClient restClient = RestClient.create();

    public VNPayTransactionResponse refund(String transactionUrl, VNPayRefundRequest request) {
        return restClient.post()
                .uri(transactionUrl)
                .body(request)
                .retrieve()
                .body(VNPayTransactionResponse.class);
    }

    public VNPayTransactionResponse queryTransaction(String transactionUrl, VNPayQueryDrRequest request) {
        return restClient.post()
                .uri(transactionUrl)
                .body(request)
                .retrieve()
                .body(VNPayTransactionResponse.class);
    }
}
