package io.hc.heavenlycoupon.coupon;

import org.springframework.stereotype.Component;

@Component
public class CouponRequestFacade {

    private final CouponRequestProcessor processor;

    public CouponRequestFacade(CouponRequestProcessor processor) {
        this.processor = processor;
    }

    // 추후에 확장할 거이기에 Void가 아닌 CouponRequestResult로 함.
    public CouponRequestResult request(CouponRequest request) {
        return processor.process(request);
    }
}