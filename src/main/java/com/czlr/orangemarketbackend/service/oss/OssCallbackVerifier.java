package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

@Component
public class OssCallbackVerifier {

    static final String PUBLIC_KEY_HTTP_PREFIX = "http://gosspublic.alicdn.com/";
    static final String PUBLIC_KEY_HTTPS_PREFIX = "https://gosspublic.alicdn.com/";

    private final OssCallbackPublicKeyProvider publicKeyProvider;

    public OssCallbackVerifier(OssCallbackPublicKeyProvider publicKeyProvider) {
        this.publicKeyProvider = publicKeyProvider;
    }

    public void verify(String requestUri, String queryString, byte[] body, String authorization, String pubKeyUrlHeader) {
        if (isBlank(authorization) || isBlank(pubKeyUrlHeader) || isBlank(requestUri) || body == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
        URI publicKeyUrl = decodePublicKeyUrl(pubKeyUrlHeader);
        PublicKey publicKey = publicKeyProvider.getPublicKey(publicKeyUrl);
        String signContent = buildSignContent(requestUri, queryString, body);
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(authorization.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
        try {
            Signature verifier = Signature.getInstance("MD5withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signContent.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signature)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
    }

    public static URI decodePublicKeyUrl(String pubKeyUrlHeader) {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(pubKeyUrlHeader.trim()), StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
        if (!decoded.startsWith(PUBLIC_KEY_HTTP_PREFIX) && !decoded.startsWith(PUBLIC_KEY_HTTPS_PREFIX)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
        try {
            return URI.create(decoded);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
    }

    public static String buildSignContent(String requestUri, String queryString, byte[] body) {
        String path = java.net.URLDecoder.decode(requestUri, StandardCharsets.UTF_8);
        String query = queryString == null || queryString.isEmpty() ? "" : "?" + queryString;
        return path + query + "\n" + new String(body, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
