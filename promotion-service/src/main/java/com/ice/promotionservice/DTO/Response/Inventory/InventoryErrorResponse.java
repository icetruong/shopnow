package com.ice.promotionservice.DTO.Response.Inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body lỗi trả về từ inventory-service: { success, errorCode, message }. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryErrorResponse {
    private Boolean success;
    private String errorCode;
    private String message;
}
