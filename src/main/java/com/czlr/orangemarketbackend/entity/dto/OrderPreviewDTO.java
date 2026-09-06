package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPreviewDTO {
    private List<OrderItemDTO> items;
    private OrderAddressDTO address;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal total;
    private Integer paymentExpireMinutes;
}
