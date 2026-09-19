package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class OssOwnedUrlValidator {

    private final AliyunOssProperties properties;

    public OssOwnedUrlValidator(AliyunOssProperties properties) {
        this.properties = properties;
    }

    public String requireOwnedUrl(String url, String requiredPathPrefix) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址格式错误");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址格式错误");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址格式错误");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址格式错误");
        }

        String objectKey = objectKeyOf(uri);
        if (objectKey.contains("..") || objectKey.contains("\\")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        if (!matchesAllowedBase(uri)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        if (requiredPathPrefix != null && !objectKey.startsWith(requiredPathPrefix)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        return properties.toAccessUrl(objectKey);
    }

    public String objectKeyOf(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        return path;
    }

    private boolean matchesAllowedBase(URI uri) {
        return matchesBase(uri, properties.getPublicBaseUrl()) || matchesBase(uri, properties.getHost());
    }

    private static boolean matchesBase(URI uri, String base) {
        if (base == null || base.isBlank()) {
            return false;
        }
        URI baseUri;
        try {
            baseUri = URI.create(base);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (baseUri.getHost() == null || uri.getHost() == null) {
            return false;
        }
        if (!baseUri.getHost().equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        int basePort = baseUri.getPort();
        int urlPort = uri.getPort();
        if (basePort != urlPort) {
            return false;
        }
        String basePath = baseUri.getPath() == null ? "" : baseUri.getPath();
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        String urlPath = uri.getPath() == null ? "" : uri.getPath();
        return basePath.isEmpty() || urlPath.equals(basePath) || urlPath.startsWith(basePath + "/");
    }
}
