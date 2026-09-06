package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DirectOrderCreateRequest {
    private String productId;
    private Integer quantity;
    private String addressId;
    private String buyerRemark;
}
