package com.ice.reviewservice.Client;

import com.ice.reviewservice.DTO.Response.User.InternalUserResponse;
import com.ice.reviewservice.Exception.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserClient {

    private final RestClient restClient;
    private final String internalToken;

    public UserClient(
            @Value("${user.service.url}") String userUrl,
            @Value("${internal.secret-token}") String secretToken
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(userUrl)
                .build();
        this.internalToken = secretToken;
    }

    public InternalUserResponse getUser(String userId)
    {
        try {
            return restClient.get()
                    .uri("/api/v1/internal/users/{id}", userId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new UserServiceUnavailableException(
                                "user-service trả lỗi " + response.getStatusCode());
                    })
                    .body(InternalUserResponse.class);
        } catch (RestClientException e) {
            throw new UserServiceUnavailableException("Không gọi được user-service, thử lại sau");
        }
    }
}
