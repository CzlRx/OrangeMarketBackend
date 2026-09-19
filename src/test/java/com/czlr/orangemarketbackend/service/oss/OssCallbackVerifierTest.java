package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssCallbackVerifierTest {

    @Test
    void rejectsPublicKeyUrlOutsideOssCdn() {
        String header = Base64.getEncoder().encodeToString(
                "https://evil.example/key.pem".getBytes(StandardCharsets.UTF_8));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> OssCallbackVerifier.decodePublicKeyUrl(header));
        assertEquals(ResultCode.BAD_REQUEST, ex.getResultCode());
    }

    @Test
    void acceptsGosspublicUrl() {
        String header = Base64.getEncoder().encodeToString(
                "https://gosspublic.alicdn.com/callback_pub_key_v1.pem".getBytes(StandardCharsets.UTF_8));
        URI url = OssCallbackVerifier.decodePublicKeyUrl(header);
        assertEquals("https://gosspublic.alicdn.com/callback_pub_key_v1.pem", url.toString());
    }

    @Test
    void verifiesValidRsaSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        byte[] body = "bucket=orange-market&object=avatars/1/a.png&scene=avatar&userId=1"
                .getBytes(StandardCharsets.UTF_8);
        String content = OssCallbackVerifier.buildSignContent("/api/oss/callback", null, body);
        Signature signer = Signature.getInstance("MD5withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(content.getBytes(StandardCharsets.UTF_8));
        String authorization = Base64.getEncoder().encodeToString(signer.sign());
        String pubKeyHeader = Base64.getEncoder().encodeToString(
                "https://gosspublic.alicdn.com/callback_pub_key_v1.pem".getBytes(StandardCharsets.UTF_8));

        OssCallbackPublicKeyProvider provider = url -> keyPair.getPublic();
        OssCallbackVerifier verifier = new OssCallbackVerifier(provider);
        assertDoesNotThrow(() -> verifier.verify(
                "/api/oss/callback", null, body, authorization, pubKeyHeader));
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        byte[] body = "object=avatars/1/a.png".getBytes(StandardCharsets.UTF_8);
        String content = OssCallbackVerifier.buildSignContent("/api/oss/callback", null, body);
        Signature signer = Signature.getInstance("MD5withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(content.getBytes(StandardCharsets.UTF_8));
        String authorization = Base64.getEncoder().encodeToString(signer.sign());
        String pubKeyHeader = Base64.getEncoder().encodeToString(
                "https://gosspublic.alicdn.com/callback_pub_key_v1.pem".getBytes(StandardCharsets.UTF_8));

        OssCallbackVerifier verifier = new OssCallbackVerifier(url -> keyPair.getPublic());
        assertThrows(BusinessException.class, () -> verifier.verify(
                "/api/oss/callback",
                null,
                "object=avatars/1/hacked.png".getBytes(StandardCharsets.UTF_8),
                authorization,
                pubKeyHeader));
    }
}
