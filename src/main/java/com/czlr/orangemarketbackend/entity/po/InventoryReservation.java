package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.InventoryReservationStatus;
import com.czlr.orangemarketbackend.entity.base.AuditableEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_reservation")
public class InventoryReservation extends AuditableEntity {
    private Long productId;
    private Long activityId;
    private Long orderId;
    private Long orderItemId;
    private Integer quantity;
    private String reservationType;
    private InventoryReservationStatus status;
    private LocalDateTime expireAt;
    private LocalDateTime releasedAt;
}
