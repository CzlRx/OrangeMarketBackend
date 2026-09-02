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
@TableName("product_media")
public class ProductMedia extends AuditableEntity {
    private Long productId;
    private String mediaType;
    private String mediaUrl;
    private String objectKey;
    private Integer isCover;
    private Integer sortOrder;
    private Integer durationSeconds;
}
