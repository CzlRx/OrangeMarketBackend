package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.entity.base.TimestampedEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends TimestampedEntity {
    private Long orderId;
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;
}
