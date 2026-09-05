package com.czlr.orangemarketbackend.entity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemDTO {
    private String id;
    private String productId;
    private Integer quantity;
    private Boolean selected;
    private ProductDTO product;
    private BigDecimal effectivePrice;
    private BigDecimal shippingFee;
}
