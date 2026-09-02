package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("user_browse_history")
public class UserBrowseHistory extends AuditableEntity {
    private Long userId;
    private Long productId;
    private LocalDateTime viewedAt;
    private BigDecimal priceAtView;
    private BigDecimal lastNotifiedPrice;
    private LocalDateTime priceDropNotifiedAt;
}
