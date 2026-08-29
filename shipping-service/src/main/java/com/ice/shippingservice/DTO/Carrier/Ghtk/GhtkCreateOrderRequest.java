package com.ice.shippingservice.DTO.Carrier.Ghtk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Body POST /services/shipment/order. GHTK dùng TÊN tỉnh/quận/xã (không dùng id).
 * VERIFY: danh sách field bắt buộc & tên field đối chiếu docs GHTK khi có token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GhtkCreateOrderRequest(
        @JsonProperty("products") List<Product> products,
        @JsonProperty("order") Order order
) {
    public GhtkCreateOrderRequest {
        products = products == null ? List.of() : List.copyOf(products);
    }

    public record Product(
            @JsonProperty("name") String name,
            @JsonProperty("weight") double weight,   // kg
            @JsonProperty("quantity") int quantity
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Order(
            @JsonProperty("id") String id,                  // orderCode
            @JsonProperty("pick_name") String pickName,
            @JsonProperty("pick_address") String pickAddress,
            @JsonProperty("pick_province") String pickProvince,
            @JsonProperty("pick_district") String pickDistrict,
            @JsonProperty("pick_tel") String pickTel,
            @JsonProperty("name") String name,
            @JsonProperty("address") String address,
            @JsonProperty("province") String province,
            @JsonProperty("district") String district,
            @JsonProperty("ward") String ward,
            @JsonProperty("hamlet") String hamlet,
            @JsonProperty("tel") String tel,
            @JsonProperty("note") String note,
            @JsonProperty("value") long value,              // để tính bảo hiểm
            @JsonProperty("pick_money") long pickMoney,      // COD
            @JsonProperty("is_freeship") int isFreeship,
            @JsonProperty("transport") String transport      // "road" | "fly"
    ) {
    }
}
