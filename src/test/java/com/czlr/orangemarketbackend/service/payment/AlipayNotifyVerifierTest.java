package com.czlr.orangemarketbackend.service.payment;

import com.alipay.api.internal.util.AlipaySignature;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlipayNotifyVerifierTest {

    @Test
    void acceptsValidRsa2Signature() throws Exception {
        KeyPair keyPair = rsa2048();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        Map<String, String> params = signedParams(privateKey);

        assertTrue(new AlipayNotifyVerifier().verify(params, publicKey));
    }

    @Test
    void rejectsTamperedAmount() throws Exception {
        KeyPair keyPair = rsa2048();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        Map<String, String> params = signedParams(privateKey);
        params.put("total_amount", "9.99");

        assertFalse(new AlipayNotifyVerifier().verify(params, publicKey));
    }

    @Test
    void rejectsEmptyParams() {
        assertFalse(new AlipayNotifyVerifier().verify(Map.of(), "public-key"));
        assertFalse(new AlipayNotifyVerifier().verify(null, "public-key"));
    }

    private static Map<String, String> signedParams(String privateKey) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", "2021007100677819");
        params.put("out_trade_no", "P1T1000ABCD");
        params.put("trade_no", "202609212200110001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "10.00");
        String content = AlipaySignature.getSignContent(params);
        params.put("sign", AlipaySignature.rsaSign(content, privateKey, "UTF-8", "RSA2"));
        params.put("sign_type", "RSA2");
        return params;
    }

    private static KeyPair rsa2048() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
