package com.ice.promotionservice.Client;

import com.ice.promotionservice.DTO.Request.Product.ProductBatchRequest;
import com.ice.promotionservice.DTO.Response.Product.ProductBatchResponse;
import com.ice.promotionservice.Exception.ProductServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProductClient {

    private final RestClient restClient;
    private final String internalToken;

    public ProductClient(
            @Value("${product.service.url}") String productUrl,
            @Value("${internal.secret-token}") String secretToken
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(productUrl)
                .build();
        this.internalToken = secretToken;
    }

    public ProductBatchResponse getBatch(ProductBatchRequest request)
    {
        try {
            return restClient.post()
                    .uri("/api/v1/internal/products/variants/batch")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ProductServiceUnavailableException(
                                "product-service trả lỗi " + res.getStatusCode());
                    })
                    .body(ProductBatchResponse.class);
        } catch (RestClientException e) {
            throw new ProductServiceUnavailableException("Không gọi được product-service, thử lại sau");
        }
    }
}
