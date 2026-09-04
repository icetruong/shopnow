package com.ice.promotionservice.DTO.Response.FlashSale;

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
public class FlashSaleActiveResponse {
    private String flashSaleId;
    private String title;
    private Instant startsAt;
    private Instant endsAt;
    private Instant serverTime;
    private List<FlashSaleActiveItemResponse> items;
}
