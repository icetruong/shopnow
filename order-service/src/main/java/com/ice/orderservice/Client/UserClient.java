package com.ice.orderservice.Client;

import com.ice.orderservice.DTO.Response.User.AddressResponse;
import com.ice.orderservice.Exception.CheckoutTokenExpiredException;
import com.ice.orderservice.Exception.ResourceNotFoundException;
import com.ice.orderservice.Exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserClient {

    private final RestClient restClient;
    private final String internalToken;

    public UserClient(
            @Value("${user.service.url}") String userUrl,
            @Value("${internal.secret-token}") String internalToken
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(userUrl)
                .build();
        this.internalToken = internalToken;
    }

    public AddressResponse getAddress(String userId, String addressId)
    {
        try
        {
            return restClient.get()
                    .uri("/api/v1/internal/users/{userId}/addresses/{addressId}", userId, addressId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new ResourceNotFoundException("addressId không tồn tại, hoặc tồn tại nhưng không thuộc về userId này");
                    })
                    .body(AddressResponse.class);
        }
        catch (ResourceNotFoundException e)
        {
            throw e;
        }
        catch (RestClientException e)
        {
            throw new ServiceUnavailableException("Không lấy được thông tin địa chỉ, vui lòng thử lại sau");
        }
    }
}
