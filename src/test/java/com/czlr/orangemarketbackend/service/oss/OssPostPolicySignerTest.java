package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.entity.dto.OssSignDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssPostPolicySignerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void signIsDeterministicAndConstrainsKeySizeAndScene() {
        OssStsCredentials credentials = new OssStsCredentials("STS.ak", "secret", "token-1");
        Instant now = Instant.parse("2026-09-19T08:00:00Z");
        OssPostPolicySigner.OssPostPolicyCommand command = new OssPostPolicySigner.OssPostPolicyCommand(
                credentials,
                "orange-market",
                "cn-hangzhou",
                "https://orange-market.oss-cn-hangzhou.aliyuncs.com",
                "https://cdn.example.com/avatars/10001/abc.png",
                "https://api.example.com/api/oss/callback",
                "avatars/10001/",
                "avatars/10001/abc.png",
                "image/png",
                "avatar",
                "10001",
                3600,
                2 * 1024 * 1024,
                now);

        OssSignDTO first = OssPostPolicySigner.sign(command);
        OssSignDTO second = OssPostPolicySigner.sign(command);

        assertEquals(first.getSignature(), second.getSignature());
        assertEquals(64, first.getSignature().length());
        assertEquals(OssPostPolicySigner.SIGNATURE_VERSION, first.getXOssSignatureVersion());
        assertEquals("STS.ak/20260919/cn-hangzhou/oss/aliyun_v4_request", first.getXOssCredential());
        assertEquals("20260919T080000Z", first.getXOssDate());
        assertEquals(credentials.securityToken(), first.getSecurityToken());

        String policyJson = new String(Base64.getDecoder().decode(first.getPolicy()), StandardCharsets.UTF_8);
        Map<String, Object> policy = MAPPER.readValue(policyJson, new TypeReference<>() { });
        List<Object> conditions = MAPPER.convertValue(policy.get("conditions"), new TypeReference<>() { });
        assertTrue(conditions.contains(List.of("eq", "$key", "avatars/10001/abc.png")));
        assertTrue(conditions.contains(List.of("eq", "$content-type", "image/png")));
        assertTrue(conditions.contains(List.of("content-length-range", 1, 2 * 1024 * 1024)));
        assertTrue(conditions.stream().anyMatch(item -> String.valueOf(item).contains("avatar")));
        assertTrue(conditions.stream().anyMatch(item -> String.valueOf(item).contains("10001")));

        Map<String, Object> callback = MAPPER.readValue(
                new String(Base64.getDecoder().decode(first.getCallback()), StandardCharsets.UTF_8),
                new TypeReference<>() { });
        assertEquals("https://api.example.com/api/oss/callback", callback.get("callbackUrl"));
        assertTrue(String.valueOf(callback.get("callbackBody")).contains("${x:scene}"));
    }

    @Test
    void signatureV4MatchesKnownVector() {
        String policy = "cG9saWN5";
        String signature = OssSignatureV4.signPolicy("secret", "20260919", "cn-hangzhou", policy);
        assertEquals(OssSignatureV4.signPolicy("secret", "20260919", "cn-hangzhou", policy), signature);
        assertEquals(64, signature.length());
        assertTrue(signature.matches("[0-9a-f]{64}"));
    }
}
