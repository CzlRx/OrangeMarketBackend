package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.PaymentStatus;
import com.czlr.orangemarketbackend.entity.base.AuditableEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("payment_transaction")
public class PaymentTransaction extends AuditableEntity {
    private Long orderId;
    private Long userId;
    private String paymentNo;
    private String paymentMethod;
    private BigDecimal amount;
    private PaymentStatus status;
    private String idempotencyKey;
    private String providerTransactionNo;
    private LocalDateTime paidAt;
}
