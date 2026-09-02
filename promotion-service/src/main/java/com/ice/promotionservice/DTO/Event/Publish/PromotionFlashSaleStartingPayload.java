package com.ice.promotionservice.DTO.Event.Publish;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PromotionFlashSaleStartingPayload {
    private String flashSaleId;
    private String title;
    private String startsAt;
}
