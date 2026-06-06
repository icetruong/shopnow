package com.ice.cartservice.DTO.Response.Inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockBatchResponse {
    List<StockItemResponse> data;
}
