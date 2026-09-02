package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.SeckillActivityStatus;
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
@TableName("seckill_activity")
public class SeckillActivity extends AuditableEntity {
    private Long productId;
    private String activityName;
    private BigDecimal seckillPrice;
    private Integer stockTotal;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer soldStock;
    private Integer purchaseLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private SeckillActivityStatus status;
}
