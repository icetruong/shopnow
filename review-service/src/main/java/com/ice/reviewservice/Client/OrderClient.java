package com.ice.reviewservice.Client;

import com.ice.reviewservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.reviewservice.Exception.OrderServiceUnavailableException;
import com.ice.reviewservice.Exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

@Component
public class OrderClient {

    private final RestClient restClient;
    private final String internalToken;

    public OrderClient(
            @Value("${order.service.url}") String orderUrl,
            @Value("${internal.secret-token}") String secretToken
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(orderUrl)
                .build();
        this.internalToken = secretToken;
    }

    public OrderDetailResponse getOrder(String orderId)
    {
        try {
            return restClient.get()
                    .uri("/api/v1/internal/orders/{orderId}", orderId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("Order không tồn tại: " + orderId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new OrderServiceUnavailableException(
                                "order-service trả lỗi " + res.getStatusCode());
                    })
                    .body(OrderDetailResponse.class);
        }
        catch (ResourceNotFoundException e) {
            throw e;                                   // giữ nguyên 404
        } catch (RestClientException e) {
            throw new OrderServiceUnavailableException("Không gọi được order-service, thử lại sau");
        }
    }

    public List<OrderDetailResponse> getOrderOfUser(String userId)
    {
        try {
            // GET /api/v1/internal/orders?userId={userId}&statuses=DELIVERED&statuses=COMPLETED
            OrderDetailResponse[] orders = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/internal/orders")
                            .queryParam("userId", userId)
                            .queryParam("statuses", "DELIVERED", "COMPLETED")
                            .build())
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new OrderServiceUnavailableException(
                                "order-service trả lỗi " + res.getStatusCode());
                    })
                    .body(OrderDetailResponse[].class);

            return orders == null ? List.of() : Arrays.asList(orders);
        } catch (RestClientException e) {
            throw new OrderServiceUnavailableException("Không gọi được order-service, thử lại sau");
        }
    }
}
