package woowacourse.payment.web;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 응답이 느린 외부 결제 게이트웨이를 흉내 내는 데모 전용 스텁. 지연(demo.slow-gateway-delay-ms)이 read timeout 보다 길면 브라우저에서 타임아웃이 재현된다.
 */
@RestController
public class LocalGatewayStubController {

  private final long delayMs;

  public LocalGatewayStubController(@Value("${demo.slow-gateway-delay-ms:4000}") long delayMs) {
    this.delayMs = delayMs;
  }

  @PostMapping("/v1/payments/confirm")
  public Map<String, Object> confirm(@RequestBody(required = false) Map<String, Object> request)
      throws InterruptedException {
    Thread.sleep(delayMs);

    var body = request != null ? request : Map.<String, Object>of();
    var response = new HashMap<String, Object>();
    response.put("paymentKey", body.getOrDefault("paymentKey", "slow-pk"));
    response.put("orderId", body.getOrDefault("orderId", "order-slow"));
    response.put("status", "DONE");
    response.put("totalAmount", body.getOrDefault("amount", 0));
    return response;
  }

}
