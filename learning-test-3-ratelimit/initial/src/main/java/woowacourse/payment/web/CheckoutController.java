package woowacourse.payment.web;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import woowacourse.payment.PaymentAmountMismatchException;
import woowacourse.payment.PaymentService;
import woowacourse.payment.client.OutboundRateLimitException;
import woowacourse.payment.history.PaymentHistory;
import woowacourse.payment.history.PaymentHistoryRepository;
import woowacourse.payment.order.Order;
import woowacourse.payment.order.OrderRepository;

/**
 * 모듈 1·2와 같은 결제 흐름(SSR)에, Rate Limit 을 체감하는 '버스트 결제'(/payments/burst)를 더한 컨트롤러. 모든 시도는 결제 내역(/payments/history)에 함께
 * 남는다.
 */
@Controller
public class CheckoutController {

  private static final long DEFAULT_AMOUNT = 50_000L;
  private static final String ORDER_NAME = "방탈출 예약 — 우아한 비밀의 방";
  private static final int MAX_BURST = 20;

  private final OrderRepository orderRepository;
  private final PaymentService paymentService;
  private final PaymentHistoryRepository historyRepository;
  private final String clientKey;

  public CheckoutController(
      OrderRepository orderRepository,
      PaymentService paymentService,
      PaymentHistoryRepository historyRepository,
      @Value("${toss.client-key:}") String clientKey
  ) {
    this.orderRepository = orderRepository;
    this.paymentService = paymentService;
    this.historyRepository = historyRepository;
    this.clientKey = clientKey;
  }

  @GetMapping("/")
  public String checkout(Model model) {
    var orderId = newId("order");
    orderRepository.save(new Order(orderId, DEFAULT_AMOUNT));

    model.addAttribute("clientKey", clientKey);
    model.addAttribute("orderId", orderId);
    model.addAttribute("orderName", ORDER_NAME);
    model.addAttribute("amount", DEFAULT_AMOUNT);
    model.addAttribute("maxBurst", MAX_BURST);
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
          true, orderId, result.approvedAmount(), paymentKey, result.status().name(), "게이트웨이 승인"));
      model.addAttribute("result", result);
      model.addAttribute("paymentKey", paymentKey);
      return "success";
    } catch (PaymentAmountMismatchException e) {
      historyRepository.save(PaymentHistory.of(false, orderId, amount, paymentKey, "금액 불일치", e.getMessage()));
      return failView(model, "AMOUNT_MISMATCH", e.getMessage(), orderId);
    } catch (OutboundRateLimitException e) {
      historyRepository.save(PaymentHistory.of(false, orderId, amount, paymentKey, "나가는 한도 차단", e.getMessage()));
      return failView(model, "OUTBOUND_RATE_LIMITED", e.getMessage(), orderId);
    } catch (RestClientException e) {
      // 게이트웨이가 429(백오프 소진) 등으로 거부한 경우.
      historyRepository.save(PaymentHistory.of(
          false, orderId, amount, paymentKey, "게이트웨이 거부", e.getClass().getSimpleName()));
      return failView(model, "GATEWAY_REJECTED", e.getMessage(), orderId);
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
   * 한 번에 N건을 보내, 한도만큼만 통과하고 나머지가 차단되는 것을 내역으로 보여준다.
   */
  @GetMapping("/payments/burst")
  public String burst(@RequestParam(defaultValue = "10") int count, Model model) {
    var n = Math.max(1, Math.min(count, MAX_BURST));
    for (var i = 0; i < n; i++) {
      var orderId = newId("order-burst");
      var paymentKey = newId("burst-pk");
      orderRepository.save(new Order(orderId, DEFAULT_AMOUNT));
      try {
        var result = paymentService.confirm(paymentKey, orderId, DEFAULT_AMOUNT);
        historyRepository.save(PaymentHistory.of(
            true, orderId, result.approvedAmount(), paymentKey, result.status().name(), "게이트웨이로 나가 승인"));
      } catch (OutboundRateLimitException e) {
        historyRepository.save(PaymentHistory.of(
            false, orderId, DEFAULT_AMOUNT, paymentKey, "나가는 한도 차단", "외부로 보내지 않음"));
      } catch (RestClientException e) {
        historyRepository.save(PaymentHistory.of(
            false, orderId, DEFAULT_AMOUNT, paymentKey, "게이트웨이 거부", e.getClass().getSimpleName()));
      }
    }
    model.addAttribute("histories", historyRepository.findAll());
    return "history";
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
