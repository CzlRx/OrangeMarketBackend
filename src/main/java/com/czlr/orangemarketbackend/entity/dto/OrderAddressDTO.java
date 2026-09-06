package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderAddressDTO {
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
}
