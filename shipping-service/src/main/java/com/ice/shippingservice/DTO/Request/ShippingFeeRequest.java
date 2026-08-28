package com.ice.shippingservice.DTO.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShippingFeeRequest {

    /** Giới hạn tham khảo theo GHN — chỉnh theo hợp đồng vận chuyển thực tế nếu cần. */
    private static final int MAX_WEIGHT_GRAM = 30_000;   // 30kg / gói (giao tiêu chuẩn)
    private static final int MAX_DIMENSION_CM = 200;

    /** Mã quận/huyện GHN nơi nhận. Client lấy từ GET /shipping/districts. */
    @NotNull(message = "toDistrictId không được để trống")
    @Positive(message = "toDistrictId phải là số dương")
    private Integer toDistrictId;

    /** Mã phường/xã GHN nơi nhận (chuỗi số). Client lấy từ GET /shipping/wards. */
    @NotBlank(message = "toWardCode không được để trống")
    @Pattern(regexp = "^[0-9]+$", message = "toWardCode chỉ gồm chữ số")
    @Size(max = 20, message = "toWardCode tối đa 20 ký tự")
    private String toWardCode;

    /** Tổng khối lượng đơn hàng, đơn vị gram. */
    @NotNull(message = "weight không được để trống")
    @Positive(message = "weight phải lớn hơn 0")
    @Max(value = MAX_WEIGHT_GRAM, message = "weight vượt giới hạn " + MAX_WEIGHT_GRAM + " gram")
    private Integer weight;

    /** Dài gói hàng (cm) — tùy chọn; bỏ trống thì dùng shipping.default-package.length. */
    @Positive(message = "length phải lớn hơn 0")
    @Max(value = MAX_DIMENSION_CM, message = "length tối đa " + MAX_DIMENSION_CM + " cm")
    private Integer length;

    /** Rộng gói hàng (cm) — tùy chọn. */
    @Positive(message = "width phải lớn hơn 0")
    @Max(value = MAX_DIMENSION_CM, message = "width tối đa " + MAX_DIMENSION_CM + " cm")
    private Integer width;

    /** Cao gói hàng (cm) — tùy chọn. */
    @Positive(message = "height phải lớn hơn 0")
    @Max(value = MAX_DIMENSION_CM, message = "height tối đa " + MAX_DIMENSION_CM + " cm")
    private Integer height;

    /** Giá trị đơn hàng để tính bảo hiểm (VND) — tùy chọn, mặc định coi như 0. */
    @PositiveOrZero(message = "insuranceValue không được âm")
    private Long insuranceValue;
}
