package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssOwnedUrlValidatorTest {

    private OssOwnedUrlValidator validator;

    @BeforeEach
    void setUp() {
        AliyunOssProperties properties = new AliyunOssProperties();
        ReflectionTestUtils.setField(properties, "bucket", "orange-market");
        ReflectionTestUtils.setField(properties, "region", "cn-hangzhou");
        ReflectionTestUtils.setField(properties, "host", "https://orange-market.oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(properties, "publicBaseUrl", "https://cdn.example.com");
        validator = new OssOwnedUrlValidator(properties);
    }

    @Test
    void acceptsPublicBaseUrlAndCanonicalizes() {
        String url = validator.requireOwnedUrl(
                "https://cdn.example.com/avatars/10001/abc.png", "avatars/10001/");
        assertEquals("https://cdn.example.com/avatars/10001/abc.png", url);
    }

    @Test
    void acceptsOssHostUrl() {
        String url = validator.requireOwnedUrl(
                "https://orange-market.oss-cn-hangzhou.aliyuncs.com/products/20260919/a.jpg",
                "products/");
        assertEquals("https://cdn.example.com/products/20260919/a.jpg", url);
    }

    @Test
    void rejectsExternalUrl() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.requireOwnedUrl("https://evil.example/avatars/10001/a.png", "avatars/10001/"));
        assertEquals(ResultCode.BAD_REQUEST, ex.getResultCode());
        assertEquals("图片地址不属于本存储空间", ex.getMessage());
    }

    @Test
    void rejectsWrongPrefixAndTraversal() {
        assertThrows(BusinessException.class,
                () -> validator.requireOwnedUrl("https://cdn.example.com/products/a.png", "avatars/10001/"));
        assertThrows(BusinessException.class,
                () -> validator.requireOwnedUrl("https://cdn.example.com/avatars/10001/../admin/a.png", "avatars/10001/"));
        assertThrows(BusinessException.class,
                () -> validator.requireOwnedUrl("https://cdn.example.com/avatars/10001/a.png?x=1", "avatars/10001/"));
    }

    @Test
    void acceptsLocalRelativeUploadUrl() {
        String url = validator.requireOwnedUrl("/api/uploads/files/avatars/10001/abc.png", "avatars/10001/");
        assertEquals("/api/uploads/files/avatars/10001/abc.png", url);
    }

    @Test
    void rejectsLocalRelativeWrongPrefix() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.requireOwnedUrl("/api/uploads/files/products/a.png", "avatars/10001/"));
        assertEquals(ResultCode.BAD_REQUEST, ex.getResultCode());
    }
}
