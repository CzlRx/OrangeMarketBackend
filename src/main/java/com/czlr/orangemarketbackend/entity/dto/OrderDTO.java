package com.czlr.orangemarketbackend.entity.dto;

import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDTO {
    private String id;
    private String orderNo;
    private OrderStatus status;
    private List<OrderItemDTO> items;
    private OrderAddressDTO address;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal total;
    private String buyerRemark;
    private String paymentMethod;
    private String trackingNo;
    private LocalDateTime createdAt;
    private LocalDateTime paymentExpireAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
}
