package com.ice.orderservice.DTO.Request.Inventory;

import com.ice.orderservice.Enum.ReasonRelease;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseRequest {
    private String orderId;
    private ReasonRelease reason;
}
