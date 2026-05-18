package com.ice.inventoryservice.DTO.Request.Inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReserveRequest {
    @NotBlank(message = "orderId must not blank")
    private String orderId;
    @Valid
    @NotEmpty(message = "items must not empty")
    private List<ItemReserveRequest> items;
}
