package com.czlr.orangemarketbackend.entity.dto;

import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateResultDTO {
    private String orderId;
    private String orderNo;
    private OrderStatus status;
    private BigDecimal total;
    private LocalDateTime paymentExpireAt;
}
