package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.OssUploadScene;
import com.czlr.orangemarketbackend.common.exception.BusinessException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OssObjectKeyFactory {

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif");

    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private static final Map<String, String> FILENAME_CONTENT_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".webp", "image/webp",
            ".gif", "image/gif");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OssObjectKeyFactory() {
    }

    public static String requireContentType(String filename, String contentType) {
        String normalizedContentType = normalizeContentType(contentType);
        if (normalizedContentType != null) {
            return normalizedContentType;
        }
        String fromName = contentTypeFromFilename(filename);
        if (fromName != null) {
            return fromName;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 jpeg/png/webp/gif 图片");
    }

    public static String extensionOf(String contentType) {
        String extension = CONTENT_TYPE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 jpeg/png/webp/gif 图片");
        }
        return extension;
    }

    public static String buildDir(OssUploadScene scene, Long userId, Clock clock) {
        if (scene == OssUploadScene.AVATAR) {
            return OssUploadScene.AVATAR.getObjectPrefix() + userId + "/";
        }
        return OssUploadScene.PRODUCT.getObjectPrefix() + LocalDate.now(clock).format(DAY) + "/";
    }

    public static String buildKey(String dir, String contentType, UUID uuid) {
        String id = uuid.toString().replace("-", "");
        return dir + id + extensionOf(contentType);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        return ALLOWED_CONTENT_TYPES.contains(normalized) ? normalized : null;
    }

    private static String contentTypeFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String lower = filename.trim().toLowerCase(Locale.ROOT);
        int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
        String name = slash >= 0 ? lower.substring(slash + 1) : lower;
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return FILENAME_CONTENT_TYPES.get(name.substring(dot));
    }
}
