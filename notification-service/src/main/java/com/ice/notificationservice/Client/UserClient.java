package com.ice.notificationservice.Client;

import com.ice.notificationservice.DTO.Response.User.InternalUserResponse;
import com.ice.notificationservice.Exception.OrderServiceUnavailableException;
import com.ice.notificationservice.Exception.ResourceNotFoundException;
import com.ice.notificationservice.Exception.UserServiceUnavailableException;
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
                    .uri("/api/v1/internal/users/{userId}", userId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("User không tồn tại: " + userId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new UserServiceUnavailableException(
                                "user-service trả lỗi " + res.getStatusCode());
                    })
                    .body(InternalUserResponse.class);
        }
        catch (ResourceNotFoundException e) {
            throw e;                                   // giữ nguyên 404
        } catch (RestClientException e) {
            throw new UserServiceUnavailableException("Không gọi được user-service, thử lại sau");
        }
    }
}
