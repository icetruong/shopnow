package com.ice.shippingservice.DTO.Response.Shipping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Trang danh sách vận đơn cho admin — format content/page/totalElements/totalPages giống order-service. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminShipmentPageResponse {
    private List<AdminShipmentItemResponse> content;
    private Integer page;
    private Long totalElements;
    private Integer totalPages;
}
