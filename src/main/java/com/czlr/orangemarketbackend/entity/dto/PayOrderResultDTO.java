package com.czlr.orangemarketbackend.entity.dto;

import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayOrderResultDTO {
    private String orderId;
    private String orderNo;
    private OrderStatus status;
    private String paymentMethod;
    private LocalDateTime paidAt;
    private String qrCode;
    private String outTradeNo;
    private LocalDateTime expireAt;
    private String payUrl;
}
