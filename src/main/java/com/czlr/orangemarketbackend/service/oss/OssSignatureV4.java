package com.czlr.orangemarketbackend.service.oss;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * OSS4-HMAC-SHA256 PostPolicy 签名，对齐阿里云服务端签名直传文档。
 */
public final class OssSignatureV4 {

    private OssSignatureV4() {
    }

    public static String signPolicy(String accessKeySecret, String date, String region, String stringToSign) {
        byte[] dateKey = hmacSha256(("aliyun_v4" + accessKeySecret).getBytes(StandardCharsets.UTF_8), date);
        byte[] dateRegionKey = hmacSha256(dateKey, region);
        byte[] dateRegionServiceKey = hmacSha256(dateRegionKey, "oss");
        byte[] signingKey = hmacSha256(dateRegionServiceKey, "aliyun_v4_request");
        return HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
    }

    static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
