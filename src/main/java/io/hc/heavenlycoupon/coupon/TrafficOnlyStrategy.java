package io.hc.heavenlycoupon.coupon;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

@Component
public class TrafficOnlyStrategy implements CouponRequestProcessor {

    private final LongAdder requestCounter = new LongAdder();

    @Override
    public CouponRequestResult process(CouponRequest request) {
        requestCounter.increment();
        return CouponRequestResult.accepted();
    }

    public long getTotalRequests() {
        return requestCounter.sum();
    }
}
