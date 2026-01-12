package io.hc.heavenlycoupon.api.dto;

import java.util.UUID;

public record CouponRequestDto (
        String userId,
        UUID requestId
) {
}
