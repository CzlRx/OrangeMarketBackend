package com.czlr.orangemarketbackend.entity.dto;

import com.czlr.orangemarketbackend.common.enums.ServiceSessionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceSessionDTO {
    private String id;
    private String userId;
    private String userNickname;
    private String agentId;
    private ServiceSessionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}
