package com.ice.orderservice.Client;

import com.ice.orderservice.DTO.Request.Inventory.DeductRequest;
import com.ice.orderservice.DTO.Request.Inventory.ReleaseRequest;
import com.ice.orderservice.DTO.Request.Inventory.ReserveRequest;
import com.ice.orderservice.DTO.Request.Inventory.ReturnRequest;
import com.ice.orderservice.DTO.Response.Common.ApiResponse;
import com.ice.orderservice.DTO.Response.Inventory.DeductResponse;
import com.ice.orderservice.DTO.Response.Inventory.ReleaseResponse;
import com.ice.orderservice.DTO.Response.Inventory.ReserveResponseSuccess;
import com.ice.orderservice.DTO.Response.Inventory.ReturnResponse;
import com.ice.orderservice.Exception.InventoryDeductFailedException;
import com.ice.orderservice.Exception.InventoryReleaseFailedException;
import com.ice.orderservice.Exception.InventoryReserveFailedException;
import com.ice.orderservice.Exception.InventoryReturnFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryClient {
    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public InventoryClient(
            @Value("${inventory.service.url}") String inventoryUrl,
            @Value("${internal.secret-token}") String secretToken,
            ObjectMapper objectMapper
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(inventoryUrl)
                .build();

        this.internalToken = secretToken;
        this.objectMapper = objectMapper;
    }

    public ReserveResponseSuccess reserve(ReserveRequest request)
    {
        try
        {
            return restClient.post()
                    .uri("/api/v1/internal/stock/reserve")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);
                        throw new InventoryReserveFailedException("Sản phẩm trong đơn không đủ hàng để đặt");
                    })
                    .body(ReserveResponseSuccess.class);
        }
        catch (RestClientException e)
        {
            throw new InventoryReserveFailedException("Không thể kiểm tra tồn kho, vui lòng thử lại sau");
        }
    }

    public ReleaseResponse release(ReleaseRequest request)
    {
        try
        {
            return restClient.post()
                    .uri("/api/v1/internal/stock/release")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);
                        throw new InventoryReleaseFailedException("Không thể hoàn tác tồn kho, vui lòng thử lại sau");
                    })
                    .body(ReleaseResponse.class);
        }
        catch (RestClientException e)
        {
            throw new InventoryReleaseFailedException("Không thể hoàn tác tồn kho, vui lòng thử lại sau");
        }
    }

    public DeductResponse deduct(DeductRequest request)
    {
        try
        {
            return restClient.post()
                    .uri("/api/v1/internal/stock/deduct")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);
                        throw new InventoryDeductFailedException("Không thể trừ kho, vui lòng thử lại sau");
                    })
                    .body(DeductResponse.class);
        }
        catch (RestClientException e)
        {
            throw new InventoryDeductFailedException("Không thể trừ kho, vui lòng thử lại sau");
        }
    }

    public ReturnResponse returnStock(ReturnRequest request)
    {
        try
        {
            return restClient.post()
                    .uri("/api/v1/internal/stock/return")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);
                        throw new InventoryReturnFailedException("Không thể hoàn kho, vui lòng thử lại sau");
                    })
                    .body(ReturnResponse.class);
        }
        catch (RestClientException e)
        {
            throw new InventoryReturnFailedException("Không thể hoàn kho, vui lòng thử lại sau");
        }
    }
}
