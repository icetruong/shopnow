package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Body POST /v2/shipping-order/create (rút gọn các field bắt buộc + hay dùng).
 * VERIFY: danh sách field bắt buộc của GHN thay đổi theo thời gian - đối chiếu docs khi có token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GhnCreateOrderRequest(
        @JsonProperty("client_order_code") String clientOrderCode,
        @JsonProperty("to_name") String toName,
        @JsonProperty("to_phone") String toPhone,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("to_ward_code") String toWardCode,
        @JsonProperty("to_district_id") int toDistrictId,
        @JsonProperty("weight") int weight,
        @JsonProperty("length") int length,
        @JsonProperty("width") int width,
        @JsonProperty("height") int height,
        @JsonProperty("service_id") long serviceId,
        @JsonProperty("payment_type_id") int paymentTypeId,
        @JsonProperty("required_note") String requiredNote,
        @JsonProperty("cod_amount") long codAmount,
        @JsonProperty("insurance_value") long insuranceValue,
        @JsonProperty("note") String note,
        @JsonProperty("items") List<Item> items
) {
    public GhnCreateOrderRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(
            @JsonProperty("name") String name,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("weight") int weight
    ) {
    }
}
