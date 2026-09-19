package com.czlr.orangemarketbackend.common.enums;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;

public enum OssUploadScene {
    AVATAR("avatar", "avatars/", 2 * 1024 * 1024),
    PRODUCT("product", "products/", 5 * 1024 * 1024);

    private final String value;
    private final String objectPrefix;
    private final int maxBytes;

    OssUploadScene(String value, String objectPrefix, int maxBytes) {
        this.value = value;
        this.objectPrefix = objectPrefix;
        this.maxBytes = maxBytes;
    }

    public String getValue() {
        return value;
    }

    public String getObjectPrefix() {
        return objectPrefix;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public static OssUploadScene fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "scene 不能为空");
        }
        String normalized = value.trim().toLowerCase();
        for (OssUploadScene scene : values()) {
            if (scene.value.equals(normalized)) {
                return scene;
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的上传场景");
    }
}
