package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.entity.base.AuditableEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("product_inventory")
public class ProductInventory extends AuditableEntity {
    private Long productId;
    private Integer stockTotal;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer soldStock;
    private Integer version;
}
