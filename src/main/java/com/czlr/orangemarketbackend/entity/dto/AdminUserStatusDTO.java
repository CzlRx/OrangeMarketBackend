package com.czlr.orangemarketbackend.entity.dto;

import com.czlr.orangemarketbackend.common.enums.CommonStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserStatusDTO {
    private String userId;
    private CommonStatus status;
    private String role;
}
