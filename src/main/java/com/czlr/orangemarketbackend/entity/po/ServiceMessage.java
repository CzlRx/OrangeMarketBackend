package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.entity.base.TimestampedEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("service_message")
public class ServiceMessage extends TimestampedEntity {
    private Long sessionId;
    private String senderType;
    private Long senderId;
    private String messageType;
    private String content;
    private Long productId;
    private Long orderId;
    private String payloadJson;
    private LocalDateTime readAt;
}
