package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmsInfo {
    private int cooldown;
    private int expiresIn;
}
