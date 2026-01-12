package io.hc.heavenlycoupon.coupon;

import java.util.UUID;

public record CouponRequest (
        String userId,
        UUID requestId
) {
}