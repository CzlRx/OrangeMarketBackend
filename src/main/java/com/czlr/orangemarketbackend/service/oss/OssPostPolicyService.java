package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.OssUploadScene;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import com.czlr.orangemarketbackend.entity.dto.OssSignDTO;
import com.czlr.orangemarketbackend.entity.dto.OssSignRequest;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class OssPostPolicyService {

    private final AliyunOssProperties properties;
    private final OssStsCredentialProvider stsCredentialProvider;
    private final UserAccountMapper userAccountMapper;
    private final Clock clock;

    public OssPostPolicyService(
            AliyunOssProperties properties,
            OssStsCredentialProvider stsCredentialProvider,
            UserAccountMapper userAccountMapper) {
        this(properties, stsCredentialProvider, userAccountMapper, Clock.systemUTC());
    }

    OssPostPolicyService(
            AliyunOssProperties properties,
            OssStsCredentialProvider stsCredentialProvider,
            UserAccountMapper userAccountMapper,
            Clock clock) {
        this.properties = properties;
        this.stsCredentialProvider = stsCredentialProvider;
        this.userAccountMapper = userAccountMapper;
        this.clock = clock;
    }

    public OssSignDTO sign(Long userId, OssSignRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或 Token 无效");
        }
        requireConfigured();
        OssUploadScene scene = OssUploadScene.fromValue(request == null ? null : request.getScene());
        if (scene == OssUploadScene.PRODUCT) {
            requireAdmin(userId);
        }
        String contentType = OssObjectKeyFactory.requireContentType(
                request.getFilename(), request.getContentType());
        String dir = OssObjectKeyFactory.buildDir(scene, userId, clock);
        String key = OssObjectKeyFactory.buildKey(dir, contentType, UUID.randomUUID());
        OssStsCredentials credentials = stsCredentialProvider.assumeRole(
                "orange-market-" + userId, properties.getExpireSeconds());
        return OssPostPolicySigner.sign(new OssPostPolicySigner.OssPostPolicyCommand(
                credentials,
                properties.getBucket().trim(),
                properties.getRegion(),
                properties.getHost(),
                properties.toAccessUrl(key),
                properties.getCallbackUrl(),
                dir,
                key,
                contentType,
                scene.getValue(),
                String.valueOf(userId),
                properties.getExpireSeconds(),
                scene.getMaxBytes(),
                clock.instant()));
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "对象存储未配置");
        }
        if (properties.getCallbackUrl().isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "对象存储回调地址未配置");
        }
        if (properties.getHost().isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "对象存储未配置");
        }
    }

    private void requireAdmin(Long userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String role = user.getRole();
        if (role == null || !"admin".equalsIgnoreCase(role.trim())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该资源");
        }
    }
}
