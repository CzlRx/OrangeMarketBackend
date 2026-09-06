package com.czlr.orangemarketbackend.entity.dto;

import com.czlr.orangemarketbackend.common.enums.CommonStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileDTO {
    private String id;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private Integer gender;
    private LocalDate birthday;
    private CommonStatus status;
    private String role;
    private LocalDateTime lastLoginAt;
}
