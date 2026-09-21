package com.czlr.orangemarketbackend.service.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.czlr.orangemarketbackend.config.AlipayProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AlipayNotifyVerifier {

    public boolean verify(Map<String, String> params, String alipayPublicKey) {
        if (params == null || params.isEmpty() || isBlank(alipayPublicKey)) {
            return false;
        }
        Map<String, String> toVerify = new HashMap<>(params);
        try {
            return AlipaySignature.rsaCheckV1(
                    toVerify,
                    alipayPublicKey,
                    AlipayProperties.CHARSET,
                    AlipayProperties.SIGN_TYPE);
        } catch (AlipayApiException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
