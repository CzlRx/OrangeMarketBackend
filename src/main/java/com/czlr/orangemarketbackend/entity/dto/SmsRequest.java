package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmsRequest {
    private String phone;
    private String purpose;
    private String captchaKey;
    private String captchaCode;
}
