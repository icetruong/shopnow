package com.ice.promotionservice.Client;

import com.ice.promotionservice.DTO.Request.Inventory.FlashSaleReleaseRequest;
import com.ice.promotionservice.DTO.Request.Inventory.FlashSaleRequest;
import com.ice.promotionservice.DTO.Request.Inventory.FlashSaleReserveRequest;
import com.ice.promotionservice.DTO.Response.Inventory.FlashSaleReserveResponse;
import com.ice.promotionservice.DTO.Response.Inventory.InventoryErrorResponse;
import com.ice.promotionservice.Enum.FlashSaleError;
import com.ice.promotionservice.Exception.FlashSaleException;
import com.ice.promotionservice.Exception.InventoryServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class InventoryClient {

    private final RestClient restClient;
    private final String internalToken;

    public InventoryClient(
            @Value("${inventory.service.url}") String inventoryUrl,
            @Value("${internal.secret-token}") String secretToken
    )
    {
        this.restClient = RestClient.builder()
                .baseUrl(inventoryUrl)
                .build();
        this.internalToken = secretToken;
    }

    public FlashSaleReserveResponse reserve(FlashSaleReserveRequest request)
    {
        try {
            return restClient.post()
                    .uri("/api/v1/internal/stock/flash-sale/reserve")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .body(FlashSaleReserveResponse.class);
        } catch (HttpClientErrorException e) {
            // 4xx: lỗi nghiệp vụ inventory trả về -> map sang FlashSaleException để passthrough đúng status/code.
            throw mapReserveBusinessError(e);
        } catch (RestClientException e) {
            // 5xx + timeout + connection refused + body không đọc được.
            throw new InventoryServiceUnavailableException("Không gọi được inventory-service, thử lại sau");
        }
    }

    public void release(FlashSaleReleaseRequest request)
    {
        try {
            restClient.post()
                    .uri("/api/v1/internal/stock/flash-sale/release")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new InventoryServiceUnavailableException(
                    "Không gọi được inventory-service (release): " + describe(e));
        }
    }

    public void warmupFlashSale(FlashSaleRequest request)
    {
        try {
            restClient.post()
                    .uri("/api/v1/internal/flash-sale-stock")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new InventoryServiceUnavailableException(
                    "Không gọi được inventory-service (warmup): " + describe(e));
        }
    }

    public void activateFlashSale(String flashSaleId)
    {
        try {
            restClient.post()
                    .uri("/api/v1/internal/flash-sale-stock/{flashSaleId}/activate", flashSaleId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new InventoryServiceUnavailableException(
                    "Không gọi được inventory-service (activate): " + describe(e));
        }
    }

    /**
     * Map body lỗi 4xx của inventory reserve sang exception nghiệp vụ.
     * errorCode không nằm trong danh sách đã biết -> coi như inventory hỏng (502).
     */
    private RuntimeException mapReserveBusinessError(HttpClientErrorException e) {
        String errorCode = extractErrorCode(e);
        if (errorCode == null) {
            return new InventoryServiceUnavailableException(
                    "inventory-service trả " + e.getStatusCode() + " không rõ errorCode");
        }
        return switch (errorCode) {
            case "FLASH_SALE_SOLD_OUT"   -> new FlashSaleException(FlashSaleError.FLASH_SALE_SOLD_OUT);
            case "FLASH_SALE_USER_LIMIT" -> new FlashSaleException(FlashSaleError.FLASH_SALE_LIMIT_REACHED);
            case "FLASH_SALE_NOT_ACTIVE" -> new FlashSaleException(FlashSaleError.FLASH_SALE_NOT_ACTIVE);
            default -> new InventoryServiceUnavailableException(
                    "inventory-service trả errorCode lạ: " + errorCode);
        };
    }

    private String extractErrorCode(HttpClientErrorException e) {
        try {
            InventoryErrorResponse body = e.getResponseBodyAs(InventoryErrorResponse.class);
            return body != null ? body.getErrorCode() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String describe(RestClientException e) {
        if (e instanceof HttpClientErrorException hce) {
            String code = extractErrorCode(hce);
            return hce.getStatusCode() + (code != null ? " " + code : "");
        }
        return e.getClass().getSimpleName();
    }
}
