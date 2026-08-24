package com.ice.orderservice.DTO.Response.Order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderPageResponse {
    private List<OrderResponse> content;
    private Integer page;
    private Long totalElements;
    private Integer totalPages;
}
