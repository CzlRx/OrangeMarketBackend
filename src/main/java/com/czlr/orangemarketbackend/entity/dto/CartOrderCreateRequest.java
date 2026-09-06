package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartOrderCreateRequest {
    private List<String> cartItemIds;
    private String addressId;
    private String buyerRemark;
}
