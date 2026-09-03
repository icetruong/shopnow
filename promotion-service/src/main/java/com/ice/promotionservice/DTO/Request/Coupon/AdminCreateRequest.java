package com.ice.promotionservice.DTO.Request.Coupon;

import com.ice.promotionservice.Enum.CouponApplicableType;
import com.ice.promotionservice.Enum.CouponDiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminCreateRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String title;

    @NotNull
    private CouponDiscountType discountType;

    @NotNull
    @Positive
    private Long discountValue;

    @Positive
    private Long maxDiscount;

    @NotNull
    @PositiveOrZero
    private Long minOrder;

    @NotNull
    @Positive
    private Integer usageLimit;

    @NotNull
    @Positive
    private Integer userLimit;

    @NotNull
    private Instant startsAt;

    @NotNull
    private Instant endsAt;

    @NotNull
    private CouponApplicableType applicableType;

    private List<UUID> applicableIds;
}
