package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import com.czlr.orangemarketbackend.entity.base.TimestampedEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("order_status_history")
public class OrderStatusHistory extends TimestampedEntity {
    private Long orderId;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private String operatorType;
    private Long operatorId;
    private String reason;
}
