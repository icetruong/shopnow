package com.ice.promotionservice.Exception;

import com.ice.promotionservice.Enum.CouponAdminError;
import lombok.Getter;

@Getter
public class CouponAdminException extends RuntimeException {
    private final CouponAdminError error;

    public CouponAdminException(CouponAdminError error) {
        super(error.getMessage());
        this.error = error;
    }
}
