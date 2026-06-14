package woowacourse.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 게이트웨이 결제 승인 성공 응답. 모르는 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayConfirmResponse(
    String paymentKey,
    String orderId,
    String status,
    Long totalAmount
) {

}
