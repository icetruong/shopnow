package com.ice.inventoryservice.DTO.Request.Inventory;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequest {
    @NotBlank(message = "orderId must not blank")
    private String orderId;
}
