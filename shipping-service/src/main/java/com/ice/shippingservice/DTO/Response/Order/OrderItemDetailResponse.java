package com.ice.shippingservice.DTO.Response.Order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemDetailResponse {
    private String variantId;
    private String productId;
    private String productName;
    private String sku;
    private String color;
    private String size;
    private String thumbnail;
    private Long unitPrice;
    private Integer qty;
    private Long subtotal;
}
