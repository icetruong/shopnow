package com.ice.promotionservice.DTO.Response.FlashSale;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FlashSaleActiveItemResponse {
    private String flashItemId;
    private String productId;
    private String variantId;
    private String productName;
    private String thumbnail;
    private Long originalPrice;
    private Long flashPrice;
    private Long discountPct;
    private Integer totalQty;
    private Integer soldQty;
    private Integer remaining;
    private Integer soldPercent;
    private Integer limitPerUser;
}
