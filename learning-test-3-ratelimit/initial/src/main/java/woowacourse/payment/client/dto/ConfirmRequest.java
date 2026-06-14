package woowacourse.payment.client.dto;

/**
 * 게이트웨이 결제 승인 요청 바디. 세 필드 모두 필수다.
 */
public record ConfirmRequest(String paymentKey, String orderId, Long amount) {

}
