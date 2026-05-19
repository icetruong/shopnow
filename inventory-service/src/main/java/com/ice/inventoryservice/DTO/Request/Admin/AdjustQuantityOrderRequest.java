package com.ice.inventoryservice.DTO.Request.Admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdjustQuantityOrderRequest {
    @NotNull(message = "quantity not null")
    private Integer newQty;
    @NotBlank(message = "note not blank")
    private String note;
}
