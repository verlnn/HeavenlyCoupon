package io.hc.heavenlycoupon.api;

import io.hc.heavenlycoupon.api.dto.CouponRequestDto;
import io.hc.heavenlycoupon.coupon.CouponRequest;
import io.hc.heavenlycoupon.coupon.CouponRequestFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coupons")
public class CouponRequestController {

    private final CouponRequestFacade couponRequestFacade;

    @PostMapping("/issue")
    public ResponseEntity<Void> issue(@RequestBody CouponRequestDto dto) {

        CouponRequest request = new CouponRequest(
                dto.userId(),
                dto.requestId()
        );

        couponRequestFacade.request(request);

        return ResponseEntity.accepted().build();
    }
}
