package com.czlr.orangemarketbackend.entity.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.czlr.orangemarketbackend.common.enums.CommonStatus;
import com.czlr.orangemarketbackend.entity.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("user_account")
public class UserAccount extends BaseEntity {
    private String phone;
    private String nickname;
    private String avatarUrl;
    private Integer gender;
    private LocalDate birthday;
    private CommonStatus status;
    private LocalDateTime lastLoginAt;
}
