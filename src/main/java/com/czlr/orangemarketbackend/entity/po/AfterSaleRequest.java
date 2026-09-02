package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.AfterSaleStatus;
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
@TableName("after_sale_request")
public class AfterSaleRequest extends AuditableEntity {
    private String afterSaleNo;
    private Long orderId;
    private Long userId;
    private String type;
    private AfterSaleStatus status;
    private String reason;
    private String description;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private LocalDateTime appliedAt;
    private LocalDateTime processedAt;
    private LocalDateTime closedAt;
}
