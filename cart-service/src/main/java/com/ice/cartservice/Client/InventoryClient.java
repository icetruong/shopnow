package com.ice.cartservice.Client;

import com.ice.cartservice.DTO.Request.StockBatchRequest;
import com.ice.cartservice.DTO.Response.Inventory.StockBatchResponse;
import com.ice.cartservice.Exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
public class InventoryClient {
    private final RestClient restClient;
    private final String internalToken;

    public InventoryClient(
            @Value("${inventory.service.url}") String inventoryUrl,
            @Value("${internal.secret-token}") String internalToken
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(inventoryUrl)
                .build();
        this.internalToken = internalToken;
    }

    public StockBatchResponse getStockBatch(List<String> variantId)
    {
        try {
            return restClient.post()
                    .uri("/api/v1/internal/stock/batch")
                    .header("X-Internal-Token", internalToken)
                    .body(new StockBatchRequest(variantId))
                    .retrieve()
                    .body(StockBatchResponse.class);
        } catch (RestClientException e) {
            log.error("Gọi inventory batch thất bại: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Không lấy được tồn kho, vui lòng thử lại sau");
        }
    }
}
