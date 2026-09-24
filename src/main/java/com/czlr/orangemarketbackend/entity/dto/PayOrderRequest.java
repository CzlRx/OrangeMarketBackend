package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayOrderRequest {
    private String paymentMethod;
    /** 支付宝场景：qr 当面扫码，wap 手机网站支付。mock 不需要。 */
    private String tradeType;
}
