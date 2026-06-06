package com.ice.cartservice.Client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
}
