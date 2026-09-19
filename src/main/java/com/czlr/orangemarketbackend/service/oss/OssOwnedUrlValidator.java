package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class OssOwnedUrlValidator {

    private static final String LOCAL_FILE_PREFIX = LocalImageUploadService.PUBLIC_PATH_PREFIX;

    private final AliyunOssProperties properties;

    public OssOwnedUrlValidator(AliyunOssProperties properties) {
        this.properties = properties;
    }

    public String requireOwnedUrl(String url, String requiredPathPrefix) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不能为空");
        }
        String trimmed = url.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址格式错误");
        }

        String localObjectKey = localObjectKeyOf(uri, trimmed);
        if (localObjectKey != null) {
            return requireObjectKey(localObjectKey, requiredPathPrefix, LOCAL_FILE_PREFIX + localObjectKey);
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
        if (!matchesAllowedBase(uri)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        return requireObjectKey(objectKey, requiredPathPrefix, properties.toAccessUrl(objectKey));
    }

    public String objectKeyOf(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith(LOCAL_FILE_PREFIX.substring(1))) {
            path = path.substring(LOCAL_FILE_PREFIX.length() - 1);
        }
        if (path.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        return path;
    }

    private String requireObjectKey(String objectKey, String requiredPathPrefix, String canonicalUrl) {
        if (objectKey.contains("..") || objectKey.contains("\\") || objectKey.startsWith("/")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        if (requiredPathPrefix != null && !objectKey.startsWith(requiredPathPrefix)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址不属于本存储空间");
        }
        return canonicalUrl;
    }

    private String localObjectKeyOf(URI uri, String original) {
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return null;
        }
        if (uri.getScheme() == null && uri.getHost() == null) {
            if (original.startsWith("//") || !original.startsWith(LOCAL_FILE_PREFIX)) {
                return null;
            }
            return original.substring(LOCAL_FILE_PREFIX.length());
        }
        if (uri.getHost() == null || !isLocalDevHost(uri.getHost())) {
            return null;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.startsWith(LOCAL_FILE_PREFIX)) {
            return null;
        }
        return path.substring(LOCAL_FILE_PREFIX.length());
    }

    private static boolean isLocalDevHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized)
                || normalized.endsWith(".localhost")) {
            return true;
        }
        String[] parts = normalized.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return octets[0] == 10
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31);
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
