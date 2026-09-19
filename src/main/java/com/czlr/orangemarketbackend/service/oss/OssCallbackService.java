package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.OssUploadScene;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import com.czlr.orangemarketbackend.entity.dto.OssCallbackResultDTO;
import com.czlr.orangemarketbackend.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OssCallbackService {

    private final OssCallbackVerifier callbackVerifier;
    private final AliyunOssProperties properties;
    private final UserProfileService userProfileService;

    public OssCallbackService(
            OssCallbackVerifier callbackVerifier,
            AliyunOssProperties properties,
            UserProfileService userProfileService) {
        this.callbackVerifier = callbackVerifier;
        this.properties = properties;
        this.userProfileService = userProfileService;
    }

    public OssCallbackResultDTO handle(HttpServletRequest request) {
        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
        callbackVerifier.verify(
                request.getRequestURI(),
                request.getQueryString(),
                body,
                request.getHeader("Authorization"),
                request.getHeader("x-oss-pub-key-url"));

        Map<String, String> form = parseForm(body);
        String bucket = form.get("bucket");
        String objectKey = form.get("object");
        String sceneValue = form.get("scene");
        String userIdValue = form.get("userId");
        if (!properties.getBucket().equals(bucket) || objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调参数错误");
        }
        if (objectKey.contains("..") || objectKey.startsWith("/") || objectKey.contains("\\")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调参数错误");
        }

        OssUploadScene scene = OssUploadScene.fromValue(sceneValue);
        Long userId = parseUserId(userIdValue);
        if (scene == OssUploadScene.AVATAR) {
            String expectedPrefix = OssUploadScene.AVATAR.getObjectPrefix() + userId + "/";
            if (!objectKey.startsWith(expectedPrefix)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调参数错误");
            }
            userProfileService.updateAvatarFromObjectKey(userId, objectKey);
        } else if (!objectKey.startsWith(OssUploadScene.PRODUCT.getObjectPrefix())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调参数错误");
        }

        return new OssCallbackResultDTO(objectKey, properties.toAccessUrl(objectKey), scene.getValue());
    }

    static Map<String, String> parseForm(byte[] body) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = new String(body, StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int index = pair.indexOf('=');
            String key = index < 0 ? pair : pair.substring(0, index);
            String value = index < 0 ? "" : pair.substring(index + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static Long parseUserId(String value) {
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) {
                throw new NumberFormatException();
            }
            return userId;
        } catch (RuntimeException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调参数错误");
        }
    }
}
