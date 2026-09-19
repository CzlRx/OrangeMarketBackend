package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import com.czlr.orangemarketbackend.entity.dto.OssSignRequest;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssPostPolicyServiceTest {

    private AliyunOssProperties properties;
    private OssStsCredentialProvider stsCredentialProvider;
    private UserAccountMapper userAccountMapper;
    private OssPostPolicyService service;

    @BeforeEach
    void setUp() {
        properties = new AliyunOssProperties();
        ReflectionTestUtils.setField(properties, "accessKeyId", "ak");
        ReflectionTestUtils.setField(properties, "accessKeySecret", "sk");
        ReflectionTestUtils.setField(properties, "stsRoleArn", "acs:ram::1:role/oss-upload");
        ReflectionTestUtils.setField(properties, "bucket", "orange-market");
        ReflectionTestUtils.setField(properties, "region", "cn-hangzhou");
        ReflectionTestUtils.setField(properties, "host", "https://orange-market.oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(properties, "publicBaseUrl", "https://cdn.example.com");
        ReflectionTestUtils.setField(properties, "callbackUrl", "https://api.example.com/api/oss/callback");
        ReflectionTestUtils.setField(properties, "expireSeconds", 3600L);
        stsCredentialProvider = mock(OssStsCredentialProvider.class);
        userAccountMapper = mock(UserAccountMapper.class);
        service = new OssPostPolicyService(
                properties,
                stsCredentialProvider,
                userAccountMapper,
                Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneOffset.UTC));
        when(stsCredentialProvider.assumeRole(anyString(), anyLong()))
                .thenReturn(new OssStsCredentials("STS.ak", "sts-secret", "token"));
    }

    @Test
    void avatarSignUsesUserPrefix() {
        var dto = service.sign(10001L, new OssSignRequest("avatar", "a.png", "image/png"));
        assertTrue(dto.getDir().startsWith("avatars/10001/"));
        assertTrue(dto.getKey().startsWith("avatars/10001/"));
        assertTrue(dto.getKey().endsWith(".png"));
        assertTrue(dto.getAccessUrl().startsWith("https://cdn.example.com/avatars/10001/"));
        verify(stsCredentialProvider).assumeRole("orange-market-10001", 3600L);
        verify(userAccountMapper, never()).selectById(10001L);
    }

    @Test
    void localCallbackUrlIsOmittedFromSign() {
        ReflectionTestUtils.setField(properties, "callbackUrl", "http://localhost:8080/api/oss/callback");
        var dto = service.sign(10001L, new OssSignRequest("avatar", "a.png", "image/png"));
        assertNull(dto.getCallback());
        assertTrue(dto.getAccessUrl().startsWith("https://cdn.example.com/avatars/10001/"));
    }

    @Test
    void productSignRequiresAdmin() {
        UserAccount user = new UserAccount();
        user.setId(8L);
        user.setRole("USER");
        when(userAccountMapper.selectById(8L)).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sign(8L, new OssSignRequest("product", "a.jpg", "image/jpeg")));
        assertEquals(ResultCode.FORBIDDEN, ex.getResultCode());
        verify(stsCredentialProvider, never()).assumeRole(anyString(), anyLong());
    }

    @Test
    void productSignAllowsAdmin() {
        UserAccount user = new UserAccount();
        user.setId(3L);
        user.setRole("ADMIN");
        when(userAccountMapper.selectById(3L)).thenReturn(user);

        var dto = service.sign(3L, new OssSignRequest("product", "cover.jpg", "image/jpeg"));
        assertTrue(dto.getDir().startsWith("products/20260919/"));
        assertTrue(dto.getKey().endsWith(".jpg"));
    }

    @Test
    void missingConfigFails() {
        ReflectionTestUtils.setField(properties, "stsRoleArn", "");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sign(1L, new OssSignRequest("avatar", "a.png", "image/png")));
        assertEquals(ResultCode.INTERNAL_SERVER_ERROR, ex.getResultCode());
    }
}
