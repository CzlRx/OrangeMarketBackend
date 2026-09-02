package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.ReviewStatus;
import com.czlr.orangemarketbackend.entity.base.AuditableEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("product_review")
public class ProductReview extends AuditableEntity {
    private Long productId;
    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private String content;
    private Integer qualityScore;
    private Integer serviceScore;
    private Integer logisticsScore;
    private Integer anonymous;
    private ReviewStatus status;
}
