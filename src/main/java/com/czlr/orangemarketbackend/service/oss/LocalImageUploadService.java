package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.OssUploadScene;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import com.czlr.orangemarketbackend.config.LocalUploadProperties;
import com.czlr.orangemarketbackend.entity.dto.OssCallbackResultDTO;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

@Service
public class LocalImageUploadService {

    public static final String PUBLIC_PATH_PREFIX = "/api/uploads/files/";

    private final AliyunOssProperties ossProperties;
    private final LocalUploadProperties localUploadProperties;
    private final UserAccountMapper userAccountMapper;
    private final Clock clock;

    @Autowired
    public LocalImageUploadService(
            AliyunOssProperties ossProperties,
            LocalUploadProperties localUploadProperties,
            UserAccountMapper userAccountMapper) {
        this(ossProperties, localUploadProperties, userAccountMapper, Clock.systemUTC());
    }

    LocalImageUploadService(
            AliyunOssProperties ossProperties,
            LocalUploadProperties localUploadProperties,
            UserAccountMapper userAccountMapper,
            Clock clock) {
        this.ossProperties = ossProperties;
        this.localUploadProperties = localUploadProperties;
        this.userAccountMapper = userAccountMapper;
        this.clock = clock;
    }

    public OssCallbackResultDTO upload(Long userId, String sceneValue, MultipartFile file) {
        if (ossProperties.isConfigured() && !ossProperties.getHost().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请使用对象存储直传");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或 Token 无效");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择图片文件");
        }
        OssUploadScene scene = OssUploadScene.fromValue(sceneValue);
        if (scene == OssUploadScene.PRODUCT) {
            requireAdmin(userId);
        }
        if (file.getSize() > scene.getMaxBytes()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    scene == OssUploadScene.AVATAR ? "头像不能超过 2MB" : "商品图不能超过 5MB");
        }
        String contentType = OssObjectKeyFactory.requireContentType(
                file.getOriginalFilename(), file.getContentType());
        String dir = OssObjectKeyFactory.buildDir(scene, userId, clock);
        String objectKey = OssObjectKeyFactory.buildKey(dir, contentType, UUID.randomUUID());
        Path target = localUploadProperties.resolvedDir().resolve(objectKey).normalize();
        if (!target.startsWith(localUploadProperties.resolvedDir())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "保存图片失败");
        }
        return new OssCallbackResultDTO(objectKey, PUBLIC_PATH_PREFIX + objectKey, scene.getValue());
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
