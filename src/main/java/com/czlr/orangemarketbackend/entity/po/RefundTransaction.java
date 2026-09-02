package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.RefundStatus;
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
@TableName("refund_transaction")
public class RefundTransaction extends AuditableEntity {
    private Long afterSaleId;
    private Long orderId;
    private String refundNo;
    private BigDecimal amount;
    private RefundStatus status;
    private String paymentMethod;
    private String providerRefundNo;
    private LocalDateTime refundedAt;
}
