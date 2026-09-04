package com.ice.inventoryservice.DTO.Request.Admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleRequest {
    @NotBlank(message = "variantId must not blank")
    private String flashSaleId;
    @NotNull(message = "start at must not null")
    private Instant startsAt;
    @NotNull(message = "end at must not null")
    private Instant endsAt;
    @Valid
    @NotEmpty(message = "item must not empty")
    List<ItemFlashSaleRequest> items;
}
