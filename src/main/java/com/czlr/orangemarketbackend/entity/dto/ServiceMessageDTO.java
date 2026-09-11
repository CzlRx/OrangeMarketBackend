package com.czlr.orangemarketbackend.entity.dto;

import com.czlr.orangemarketbackend.common.enums.ServiceMessageSenderType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceMessageDTO {
    private String id;
    private String sessionId;
    private ServiceMessageSenderType senderType;
    private String senderId;
    private String content;
    private LocalDateTime createdAt;
}
