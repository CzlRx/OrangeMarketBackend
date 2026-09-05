package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartDTO {
    private List<CartItemDTO> items;
    private Integer selectedCount;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal total;
}
