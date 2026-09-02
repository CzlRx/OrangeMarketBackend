package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.SeckillPurchaseStatus;
import com.czlr.orangemarketbackend.entity.base.AuditableEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_user_purchase")
public class SeckillUserPurchase extends AuditableEntity {
    private Long activityId;
    private Long userId;
    private Long orderId;
    private Integer quantity;
    private SeckillPurchaseStatus status;
}
