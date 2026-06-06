package com.ice.cartservice.Client;

import com.ice.cartservice.DTO.Request.StockBatchRequest;
import com.ice.cartservice.DTO.Response.Inventory.StockBatchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

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
        try
        {
            return restClient.post()
                    .uri("/api/v1/internal/stock/batch")
                    .header("X-Internal-Token", internalToken)
                    .body(new StockBatchRequest(variantId))
                    .retrieve()
                    .body(StockBatchResponse.class);
        }
        catch (Exception e)
        {
            return new StockBatchResponse(List.of());
        }
    }
}
