package com.czlr.orangemarketbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AliyunOssProperties {

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.sts-role-arn:}")
    private String stsRoleArn;

    @Value("${aliyun.oss.bucket:}")
    private String bucket;

    @Value("${aliyun.oss.region:cn-hangzhou}")
    private String region;

    @Value("${aliyun.oss.host:}")
    private String host;

    @Value("${aliyun.oss.public-base-url:}")
    private String publicBaseUrl;

    @Value("${aliyun.oss.callback-url:}")
    private String callbackUrl;

    @Value("${aliyun.oss.expire-seconds:3600}")
    private long expireSeconds;

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public String getStsRoleArn() {
        return stsRoleArn;
    }

    public String getBucket() {
        return bucket;
    }

    public String getRegion() {
        return region == null || region.isBlank() ? "cn-hangzhou" : region.trim();
    }

    public String getHost() {
        if (hasText(host)) {
            return trimTrailingSlash(host.trim());
        }
        if (!hasText(bucket)) {
            return "";
        }
        return "https://" + bucket.trim() + ".oss-" + getRegion() + ".aliyuncs.com";
    }

    public String getPublicBaseUrl() {
        if (hasText(publicBaseUrl)) {
            return trimTrailingSlash(publicBaseUrl.trim());
        }
        return getHost();
    }

    public String getCallbackUrl() {
        return callbackUrl == null ? "" : callbackUrl.trim();
    }

    public long getExpireSeconds() {
        return expireSeconds > 0 ? expireSeconds : 3600L;
    }

    public String getStsEndpoint() {
        return "sts." + getRegion() + ".aliyuncs.com";
    }

    public boolean isConfigured() {
        return hasText(accessKeyId)
                && hasText(accessKeySecret)
                && hasText(stsRoleArn)
                && hasText(bucket)
                && hasText(getRegion());
    }

    public String toAccessUrl(String objectKey) {
        return getPublicBaseUrl() + "/" + objectKey;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
