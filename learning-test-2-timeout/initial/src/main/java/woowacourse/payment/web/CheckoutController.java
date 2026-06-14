package woowacourse.payment.web;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import woowacourse.payment.PaymentAmountMismatchException;
import woowacourse.payment.PaymentConfirmation;
import woowacourse.payment.PaymentService;
import woowacourse.payment.client.TossClientConfig;
import woowacourse.payment.client.TossPaymentGateway;
import woowacourse.payment.history.PaymentHistory;
import woowacourse.payment.history.PaymentHistoryRepository;
import woowacourse.payment.order.Order;
import woowacourse.payment.order.OrderRepository;

/**
 * 모듈 1과 같은 결제 흐름(SSR)에, 타임아웃을 체감하는 '느린 게이트웨이로 결제'(/payments/slow)를 더한 컨트롤러. 모든 시도는 결제 내역(/payments/history)에 함께 남는다.
 */
@Controller
public class CheckoutController {

  private static final long DEFAULT_AMOUNT = 50_000L;
  private static final String ORDER_NAME = "방탈출 예약 — 우아한 비밀의 방";

  private final OrderRepository orderRepository;
  private final PaymentService paymentService;
  private final PaymentHistoryRepository historyRepository;
  private final String clientKey;

  // '느린 게이트웨이' 호출용. 학습 대상 메서드(TossClientConfig.tossRestClient)로 직접 클라이언트를 만든다.
  private final String selfBaseUrl;
  private final String secretKey;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;

  public CheckoutController(
      OrderRepository orderRepository,
      PaymentService paymentService,
      PaymentHistoryRepository historyRepository,
      @Value("${toss.client-key:}") String clientKey,
      @Value("${server.port:8080}") int port,
      @Value("${toss.secret-key}") String secretKey,
      @Value("${toss.connect-timeout-ms}") int connectTimeoutMs,
      @Value("${toss.read-timeout-ms}") int readTimeoutMs
  ) {
    this.orderRepository = orderRepository;
    this.paymentService = paymentService;
    this.historyRepository = historyRepository;
    this.clientKey = clientKey;
    this.selfBaseUrl = "http://localhost:" + port;
    this.secretKey = secretKey;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
  }

  @GetMapping("/")
  public String checkout(Model model) {
    var orderId = newId("order");
    orderRepository.save(new Order(orderId, DEFAULT_AMOUNT));

    model.addAttribute("clientKey", clientKey);
    model.addAttribute("orderId", orderId);
    model.addAttribute("orderName", ORDER_NAME);
    model.addAttribute("amount", DEFAULT_AMOUNT);
    model.addAttribute("readTimeoutMs", readTimeoutMs);
    return "checkout";
  }

  @GetMapping("/payments/success")
  public String success(
      @RequestParam String paymentKey,
      @RequestParam String orderId,
      @RequestParam Long amount,
      Model model
  ) {
    try {
      var result = paymentService.confirm(paymentKey, orderId, amount);
      historyRepository.save(PaymentHistory.of(
          true, orderId, result.approvedAmount(), paymentKey, result.status().name(), "실제 Toss 승인"));
      model.addAttribute("result", result);
      model.addAttribute("paymentKey", paymentKey);
      return "success";
    } catch (PaymentAmountMismatchException e) {
      historyRepository.save(PaymentHistory.of(false, orderId, amount, paymentKey, "금액 불일치", e.getMessage()));
      return failView(model, "AMOUNT_MISMATCH", e.getMessage(), orderId);
    } catch (RestClientException e) {
      // 타임아웃/연결 실패를 포함한 게이트웨이 통신 오류(이 모듈의 주제).
      historyRepository.save(PaymentHistory.of(
          false, orderId, amount, paymentKey, "통신 오류/타임아웃", e.getClass().getSimpleName()));
      return failView(model, "GATEWAY_ERROR", e.getMessage(), orderId);
    }
  }

  @GetMapping("/payments/fail")
  public String fail(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String message,
      @RequestParam(required = false) String orderId,
      Model model
  ) {
    historyRepository.save(PaymentHistory.of(false, orderId, null, null, code != null ? code : "취소", message));
    return failView(model, code, message, orderId);
  }

  /**
   * 느린 로컬 게이트웨이 호출 — 타임아웃 설정 여부에 따라 결과가 갈리는 것을 내역으로 보여준다.
   */
  @GetMapping("/payments/slow")
  public String slowCheckout(Model model) {
    var orderId = newId("order-slow");
    var paymentKey = newId("slow-pk");
    orderRepository.save(new Order(orderId, DEFAULT_AMOUNT));

    var start = System.nanoTime();
    try {
      var slowClient = new TossClientConfig()
          .tossRestClient(selfBaseUrl, secretKey, connectTimeoutMs, readTimeoutMs);
      var result = new TossPaymentGateway(slowClient)
          .confirm(new PaymentConfirmation(paymentKey, orderId, DEFAULT_AMOUNT));
      var elapsedMs = (System.nanoTime() - start) / 1_000_000;
      historyRepository.save(PaymentHistory.of(true, orderId, result.approvedAmount(), paymentKey,
          result.status().name(), "느린 게이트웨이를 " + elapsedMs + "ms 끝까지 기다림 — 타임아웃 미설정(initial)"));
      model.addAttribute("result", result);
      model.addAttribute("paymentKey", paymentKey);
      return "success";
    } catch (RestClientException e) {
      var elapsedMs = (System.nanoTime() - start) / 1_000_000;
      var detail =
          "read timeout(" + readTimeoutMs + "ms) 부근(" + elapsedMs + "ms)에서 끊음 — " + e.getClass().getSimpleName();
      historyRepository.save(PaymentHistory.of(false, orderId, DEFAULT_AMOUNT, paymentKey, "타임아웃", detail));
      return failView(model, "TIMEOUT", detail, orderId);
    }
  }

  @GetMapping("/payments/history")
  public String history(Model model) {
    model.addAttribute("histories", historyRepository.findAll());
    return "history";
  }

  private String failView(Model model, String code, String message, String orderId) {
    model.addAttribute("code", code);
    model.addAttribute("message", message);
    model.addAttribute("orderId", orderId);
    return "fail";
  }

  private String newId(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
  }

}
