package com.ice.inventoryservice.DTO.Response.Admin;

import com.ice.inventoryservice.Enum.StockTransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoryTransactionResponse {
    private String transactionId;
    private StockTransactionType type;
    private Integer qty;
    private Integer qtyBefore;
    private Integer qtyAfter;
    private String orderId;
    private String note;
    private Instant createdAt;
}
