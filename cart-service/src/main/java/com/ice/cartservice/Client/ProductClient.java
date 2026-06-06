package com.ice.cartservice.Client;

import com.ice.cartservice.DTO.Request.ProductBatchRequest;
import com.ice.cartservice.DTO.Response.Inventory.ProductBatchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class ProductClient {
    private final RestClient restClient;
    private final String internalToken;

    public ProductClient(
            @Value("${product.service.url}") String inventoryUrl,
            @Value("${internal.secret-token}") String internalToken
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(inventoryUrl)
                .build();
        this.internalToken = internalToken;
    }

    public ProductBatchResponse getProductBatch(List<String> variantIds)
    {
        try
        {
            return restClient.post()
                    .uri("/api/v1/internal/products/variants/batch")
                    .header("X-Internal-Token", internalToken)
                    .body(new ProductBatchRequest(variantIds))
                    .retrieve()
                    .body(ProductBatchResponse.class);
        }
        catch (Exception e)
        {
            return new  ProductBatchResponse(List.of());
        }

    }
}
