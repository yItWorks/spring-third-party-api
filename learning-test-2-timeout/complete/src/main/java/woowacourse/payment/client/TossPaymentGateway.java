package woowacourse.payment.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import woowacourse.payment.PaymentConfirmation;
import woowacourse.payment.PaymentGateway;
import woowacourse.payment.PaymentResult;
import woowacourse.payment.PaymentStatus;
import woowacourse.payment.client.dto.ConfirmRequest;
import woowacourse.payment.client.dto.TossPaymentResponse;

/**
 * PaymentGateway 포트의 Toss 구현(어댑터). 모듈 주제가 타임아웃이라 에러코드 매핑(모듈 1)은 생략했다.
 */
@Component
public class TossPaymentGateway implements PaymentGateway {

  private final RestClient tossRestClient;

  public TossPaymentGateway(RestClient tossRestClient) {
    this.tossRestClient = tossRestClient;
  }

  @Override
  public PaymentResult confirm(PaymentConfirmation confirmation) {
    var request = new ConfirmRequest(
        confirmation.paymentKey(), confirmation.orderId(), confirmation.amount());
    var response = tossRestClient.post()
        .uri("/v1/payments/confirm")
        .contentType(MediaType.APPLICATION_JSON)
        // 타임아웃으로 끊긴 뒤 재시도해도 같은 결제로 식별되도록 멱등키를 싣는다.
        // 한 주문은 한 번만 승인되므로 주문 식별자를 키로 쓴다 — 재시도 간 같은 값이기만 하면 된다.
        .header("Idempotency-Key", confirmation.orderId())
        .body(request)
        .retrieve()
        .body(TossPaymentResponse.class);
    return new PaymentResult(
        response.paymentKey(),
        response.orderId(),
        PaymentStatus.from(response.status()),
        response.totalAmount()
    );
  }

}
