package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginDTO {
    private String Token;
    private String expiresAt;
    private boolean isNewUser;
}
