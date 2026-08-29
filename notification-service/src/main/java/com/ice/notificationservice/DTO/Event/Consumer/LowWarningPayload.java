package com.ice.notificationservice.DTO.Event.Consumer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowWarningPayload {
    private String variantId;
    private String sku;
    private Integer currentStock;
    private Integer threshold;
}
