package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.SmsLogStatus;
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
@TableName("auth_sms_log")
public class AuthSmsLog extends TimestampedEntity {
    private String phone;
    private String purpose;
    private String codeDigest;
    private String requestIp;
    private SmsLogStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
