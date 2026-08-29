package com.ice.shippingservice.DTO.Response.Shipping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Trang danh sách vận đơn cho admin — format content/page/size/totalElements/totalPages (giống payment-service). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminShipmentPageResponse {
    private List<AdminShipmentItemResponse> content;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
