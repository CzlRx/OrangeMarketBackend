package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.ProductStatus;
import com.czlr.orangemarketbackend.entity.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {
    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private BigDecimal salePrice;
    private BigDecimal originalPrice;
    private BigDecimal shippingFee;
    private Integer salesCount;
    private BigDecimal ratingAvg;
    private Integer reviewCount;
    private String tagsJson;
    private ProductStatus status;
    private Integer sortOrder;
}
