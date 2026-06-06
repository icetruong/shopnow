package com.ice.cartservice.Client;

import com.ice.cartservice.DTO.Request.Product.ProductBatchRequest;
import com.ice.cartservice.DTO.Response.Inventory.ProductBatchResponse;
import com.ice.cartservice.Exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
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
        try {
            return restClient.post()
                    .uri("/api/v1/internal/products/variants/batch")
                    .header("X-Internal-Token", internalToken)
                    .body(new ProductBatchRequest(variantIds))
                    .retrieve()
                    .body(ProductBatchResponse.class);
        } catch (RestClientException e) {
            log.error("Gọi product batch thất bại: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Không lấy được thông tin sản phẩm, vui lòng thử lại sau");
        }
    }
}
