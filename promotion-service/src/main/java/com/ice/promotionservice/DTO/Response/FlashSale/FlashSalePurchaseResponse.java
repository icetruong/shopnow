package com.ice.promotionservice.DTO.Response.FlashSale;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FlashSalePurchaseResponse {
    private Long flashPrice;
    private Long remaining;
    private Instant reservedAt;
}
