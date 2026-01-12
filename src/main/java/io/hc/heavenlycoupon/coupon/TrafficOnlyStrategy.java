package io.hc.heavenlycoupon.coupon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Component
public class TrafficOnlyStrategy implements CouponRequestProcessor {

    private final LongAdder requestCounter = new LongAdder();

    @Override
    public CouponRequestResult process(CouponRequest request) {
        requestCounter.increment();
        log.info("====== {} ======", getTotalRequests());
        return CouponRequestResult.accepted();
    }

    public long getTotalRequests() {
        return requestCounter.sum();
    }
}
