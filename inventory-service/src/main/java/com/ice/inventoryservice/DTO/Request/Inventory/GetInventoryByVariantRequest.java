package com.ice.inventoryservice.DTO.Request.Inventory;

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
public class GetInventoryByVariantRequest {
    @NotEmpty(message = "variantIds not empty")
    List<String> variantIds;
}
