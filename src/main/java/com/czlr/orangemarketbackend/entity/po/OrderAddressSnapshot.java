package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.entity.base.TimestampedEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("order_address_snapshot")
public class OrderAddressSnapshot extends TimestampedEntity {
    private Long orderId;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
}
