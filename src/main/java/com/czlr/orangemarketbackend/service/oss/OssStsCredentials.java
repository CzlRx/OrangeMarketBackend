package com.czlr.orangemarketbackend.service.oss;

public record OssStsCredentials(
        String accessKeyId,
        String accessKeySecret,
        String securityToken) {
}
