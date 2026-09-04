package com.ice.promotionservice.DTO.Request.FlashSale;

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
@AllArgsConstructor
@NoArgsConstructor
public class CreateAdminFlashSaleRequest {

    @NotBlank
    private String title;

    @NotNull
    private Instant startsAt;

    @NotNull
    private Instant endsAt;

    @NotEmpty
    @Valid
    private List<CreateAdminFlashSaleItemRequest> items;
}
