package com.ice.cartservice.Model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemCheckoutToken {
    private String cartItemId;
    private String variantId;
    private String productId;
    private String productName;
    private String color;
    private String size;
    private String thumbnail;
    private String sku;
    private Long unitPrice;
    private Integer qty;
    private Long subtotal;
}
