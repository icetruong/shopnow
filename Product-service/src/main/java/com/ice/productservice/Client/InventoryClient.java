package com.ice.productservice.Client;

import java.io.IOException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.productservice.DTO.Request.Internal.StockBatchRequest;
import com.ice.productservice.DTO.Request.Internal.StockItemPostRequest;
import com.ice.productservice.DTO.Request.Internal.StockPostRequest;
import com.ice.productservice.DTO.Response.Common.ApiResponse;
import com.ice.productservice.DTO.Response.Internal.StockBatchResponse;
import com.ice.productservice.Exception.AlreadyExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class InventoryClient {
    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public StockBatchResponse getBatchStock(List<String> variantIds)
    {
        try
        {
            return restClient.post()
                    .uri("/api/v1/internal/stock/batch")
                    .header("X-Internal-Token", internalToken)
                    .body(new StockBatchRequest(variantIds))
                    .retrieve()
                    .body(StockBatchResponse.class);
        }
        catch (Exception e)
        {
            return new StockBatchResponse(List.of());
        }
    }

    public void insertStock(StockItemPostRequest request)
    {
        restClient.post()
                .uri("/api/v1/internal/stock")
                .header("X-Internal-Token", internalToken)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 409, (req, res) -> {
                    try {
                        ApiResponse<Void> body = objectMapper.readValue(
                                res.getBody(), new TypeReference<ApiResponse<Void>>() {}
                        );
                        throw new AlreadyExistsException(body.getMessage());
                    } catch (IOException parseEx) {
                        throw new AlreadyExistsException("Inventory đã tồn tại cho variantId: " + request.getVariantId());
                    }
                })
                .toBodilessEntity();
    }

    public void insertAllStock(StockPostRequest request)
    {
        restClient.post()
                .uri("/api/v1/internal/stock/bulk")
                .header("X-Internal-Token", internalToken)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 409, (req, res) -> {
                    try {
                        ApiResponse<List<String>> body = objectMapper.readValue(
                                res.getBody(), new TypeReference<ApiResponse<List<String>>>() {}
                        );
                        String conflictIds = String.join(", ", body.getData());
                        throw new AlreadyExistsException(body.getMessage() + ": " + conflictIds);
                    } catch (IOException parseEx) {
                        throw new AlreadyExistsException("Một số variantId đã tồn tại trong inventory");
                    }
                })
                .toBodilessEntity();
    }
}
