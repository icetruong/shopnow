package com.ice.searchservice.Client;

import com.ice.searchservice.DTO.Response.Product.ProductReindexPageResponse;
import com.ice.searchservice.Exception.ProductServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProductClient {
    public static final int PAGE_SIZE = 500;

    private final RestClient restClient;
    private final String internalToken;

    public ProductClient(
            @Value("${product.service.url}") String productUrl,
            @Value("${internal.secret-token}") String secretToken
    )
    {
        this.restClient = RestClient
                .builder()
                .baseUrl(productUrl)
                .build();
        this.internalToken = secretToken;
    }

    public ProductReindexPageResponse getProducts(int page)
    {
        try {
            return restClient.get()
                    .uri("/api/v1/internal/products?page={page}&size={size}", page, PAGE_SIZE)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ProductServiceUnavailableException(
                                "product-service trả lỗi " + res.getStatusCode());
                    })
                    .body(ProductReindexPageResponse.class);
        } catch (RestClientException e) {
            throw new ProductServiceUnavailableException("Không gọi được product-service, thử lại sau");
        }
    }
}
