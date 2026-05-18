package com.ice.inventoryservice.DTO.Request.Inventory;

import com.ice.inventoryservice.Enum.ReasonRelease;
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
public class ReleaseRequest {
    @NotBlank(message = "orderId must not blank")
    private String orderId;
    @NotNull(message = "reason not null")
    private ReasonRelease reason;
}
