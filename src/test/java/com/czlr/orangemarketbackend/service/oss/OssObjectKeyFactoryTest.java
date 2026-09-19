package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.OssUploadScene;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssObjectKeyFactoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void requireContentTypeAcceptsJpegAliasAndFilename() {
        assertEquals("image/jpeg", OssObjectKeyFactory.requireContentType("a.jpg", "image/jpg"));
        assertEquals("image/png", OssObjectKeyFactory.requireContentType("photo.PNG", null));
        assertEquals("image/webp", OssObjectKeyFactory.requireContentType(null, "image/webp; charset=utf-8"));
    }

    @Test
    void requireContentTypeRejectsUnsupportedType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> OssObjectKeyFactory.requireContentType("note.pdf", "application/pdf"));
        assertEquals(ResultCode.BAD_REQUEST, ex.getResultCode());
    }

    @Test
    void buildAvatarAndProductKeys() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String avatarDir = OssObjectKeyFactory.buildDir(OssUploadScene.AVATAR, 10001L, CLOCK);
        String productDir = OssObjectKeyFactory.buildDir(OssUploadScene.PRODUCT, 9L, CLOCK);
        assertEquals("avatars/10001/", avatarDir);
        assertEquals("products/20260919/", productDir);
        assertEquals("avatars/10001/123e4567e89b12d3a456426614174000.png",
                OssObjectKeyFactory.buildKey(avatarDir, "image/png", uuid));
        assertTrue(OssObjectKeyFactory.buildKey(productDir, "image/jpeg", uuid).startsWith("products/20260919/"));
        assertTrue(OssObjectKeyFactory.buildKey(productDir, "image/jpeg", uuid).endsWith(".jpg"));
    }
}
