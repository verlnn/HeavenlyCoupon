package io.hc.heavenlycoupon.coupon;

public record CouponRequestResult (
    RequestStatus status
) {
    public static CouponRequestResult accepted() {
        return new CouponRequestResult(RequestStatus.ACCEPTED);
    }
}