package com.ice.orderservice.Client;

import com.ice.orderservice.DTO.Request.Payment.CreatePaymentRequest;
import com.ice.orderservice.DTO.Request.Payment.RefundPaymentRequest;
import com.ice.orderservice.DTO.Response.Common.ApiResponse;
import com.ice.orderservice.DTO.Response.Payment.*;
import com.ice.orderservice.Enum.PaymentMethod;
import com.ice.orderservice.Exception.PaymentCreationFailedException;
import com.ice.orderservice.Exception.PaymentRefundFailException;
import com.ice.orderservice.Exception.PaymentLookupFailedException;
import com.ice.orderservice.Exception.PaymentConfirmCodFailedException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PaymentClient {
    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper;


    public PaymentClient(
            @Value("${payment.service.url}") String paymentUrl,
            @Value("${internal.secret-token}") String secretToken,
            ObjectMapper objectMapper
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(paymentUrl)
                .build();
        this.internalToken = secretToken;
        this.objectMapper = objectMapper;
    }

    public PaymentCreationResult createPayment(CreatePaymentRequest request)
    {
        try
        {
            RestClient.ResponseSpec response = restClient.post()
                    .uri("/api/v1/internal/payments/create")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);
                        throw new PaymentCreationFailedException("Không thể khởi tạo thanh toán, vui lòng thử lại sau");
                    });

            if(request.getMethod() == PaymentMethod.COD)
                return new PaymentCreationResult.Cod(response.body(CreatePaymentCODResponse.class));

            return new PaymentCreationResult.Online(response.body(CreatePaymentOnlineResponse.class));
        }

        catch (RestClientException e)
        {
            throw new PaymentCreationFailedException("Không thể khởi tạo thanh toán, vui lòng thử lại sau");
        }
    }

    public RefundPaymentResponse refundPayment(RefundPaymentRequest request, String paymentId)
    {
        try
        {
            return restClient.post()
                    .uri("/api/v1/internal/payments/{paymentId}/refund", paymentId)
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);
                        throw new PaymentRefundFailException("Không thể hoàn tiền, vui lòng thử lại sau");
                    })
                    .body(RefundPaymentResponse.class);
        }
        catch (RestClientException e)
        {
            throw new PaymentRefundFailException("Không thể hoàn tiền, vui lòng thử lại sau");
        }
    }

    public PaymentInternalResponse getPaymentByOrderId(String orderId)
    {
        try
        {
            return restClient.get()
                    .uri("/api/v1/internal/payments/order/{orderId}", orderId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);
                        throw new PaymentLookupFailedException("Không tìm thấy thông tin thanh toán của đơn hàng");
                    })
                    .body(PaymentInternalResponse.class);
        }
        catch (RestClientException e)
        {
            throw new PaymentLookupFailedException("Không tìm thấy thông tin thanh toán của đơn hàng");
        }
    }

    public ConfirmCodPaymentResponse confirmCod(String paymentId)
    {
        try
        {
            return restClient.patch()
                    .uri("/api/v1/internal/payments/{paymentId}/confirm-cod", paymentId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);
                        throw new PaymentConfirmCodFailedException("Không thể xác nhận thanh toán COD, vui lòng thử lại sau");
                    })
                    .body(ConfirmCodPaymentResponse.class);
        }
        catch (RestClientException e)
        {
            throw new PaymentConfirmCodFailedException("Không thể xác nhận thanh toán COD, vui lòng thử lại sau");
        }
    }
}
