package com.ice.orderservice.Client;

import com.ice.orderservice.DTO.Response.Cart.CartCheckoutTokenResponse;
import com.ice.orderservice.Exception.CheckoutTokenExpiredException;
import com.ice.orderservice.Exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CartClient {
    private final RestClient restClient;
    private final String internalToken;

    public CartClient(
            @Value("${cart.service.url}") String cartUrl,
            @Value("${internal.secret-token}") String internalToken
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(cartUrl)
                .build();
        this.internalToken = internalToken;
    }

    public CartCheckoutTokenResponse checkoutToken(String checkoutToken)
    {
        try
        {
            return restClient.get()
                    .uri("/api/v1/internal/cart/checkout/{checkoutToken}", checkoutToken)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new CheckoutTokenExpiredException("Phiên thanh toán hết hạn, vui lòng đặt lại.");
                    })
                    .body(CartCheckoutTokenResponse.class);
        }
        catch (CheckoutTokenExpiredException e)
        {
            throw e;
        }
        catch (RestClientException e)
        {
            throw new ServiceUnavailableException("Không lấy được thông tin giỏ hàng, vui lòng thử lại sau");
        }
    }
}
