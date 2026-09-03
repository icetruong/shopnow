package com.ice.promotionservice.DTO.Request.Coupon;

import com.ice.promotionservice.Enum.CouponApplicableType;
import com.ice.promotionservice.Enum.CouponDiscountType;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Partial update — field nào null thì giữ nguyên giá trị cũ. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CouponUpdateRequest {

    /** Chỉ dùng để phát hiện cố tình đổi code, KHÔNG cập nhật. */
    private String code;

    private String title;

    private CouponDiscountType discountType;

    @Positive
    private Long discountValue;

    @Positive
    private Long maxDiscount;

    @PositiveOrZero
    private Long minOrder;

    @Positive
    private Integer usageLimit;

    @Positive
    private Integer userLimit;

    private Instant startsAt;

    private Instant endsAt;

    private CouponApplicableType applicableType;

    private List<UUID> applicableIds;

    private Boolean isActive;
}
