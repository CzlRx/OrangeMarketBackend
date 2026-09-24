package com.czlr.orangemarketbackend.config;

import com.alipay.api.AlipayConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class AlipayProperties {

    public static final String CHARSET = "UTF-8";
    public static final String FORMAT = "json";
    public static final String SIGN_TYPE = "RSA2";
    public static final String ENCRYPT_TYPE = "AES";
    public static final String DEFAULT_GATEWAY = "https://openapi.alipay.com/gateway.do";

    @Value("${alipay.gateway:" + DEFAULT_GATEWAY + "}")
    private String gateway;

    @Value("${alipay.app-id:}")
    private String appId;

    @Value("${alipay.merchant-private-key:}")
    private String merchantPrivateKey;

    @Value("${alipay.alipay-public-key:}")
    private String alipayPublicKey;

    @Value("${alipay.encrypt-key:}")
    private String encryptKey;

    @Value("${alipay.notify-url:}")
    private String notifyUrl;

    @Value("${alipay.trade-mode:}")
    private String tradeMode;

    @Value("${alipay.return-base-url:}")
    private String returnBaseUrl;

    @Value("${alipay.allow-mock:true}")
    private boolean allowMock;

    public String getGateway() {
        String value = trimToEmpty(gateway);
        return value.isEmpty() ? DEFAULT_GATEWAY : value;
    }

    public String getAppId() {
        return trimToEmpty(appId);
    }

    public String getMerchantPrivateKey() {
        return normalizeKey(merchantPrivateKey);
    }

    public String getAlipayPublicKey() {
        return normalizeKey(alipayPublicKey);
    }

    public String getEncryptKey() {
        return trimToEmpty(encryptKey);
    }

    public String getNotifyUrl() {
        return trimToEmpty(notifyUrl);
    }

    public boolean isAllowMock() {
        return allowMock;
    }

    /**
     * 沙箱网关默认走电脑网站支付，浏览器里用沙箱买家账号付款。
     * 显式设置 precreate / page 时以配置为准。
     */
    public boolean isPageMode() {
        String mode = trimToEmpty(tradeMode).toLowerCase(Locale.ROOT);
        if ("page".equals(mode)) {
            return true;
        }
        if ("precreate".equals(mode)) {
            return false;
        }
        String gatewayUrl = getGateway().toLowerCase(Locale.ROOT);
        return gatewayUrl.contains("alipaydev.com") || gatewayUrl.contains("sandbox");
    }

    public String returnUrlFor(long orderId) {
        String base = trimToEmpty(returnBaseUrl);
        if (base.isEmpty()) {
            return null;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/payment/" + orderId + "?alipayReturn=1";
    }

    public boolean isConfigured() {
        return !getAppId().isEmpty()
                && !getMerchantPrivateKey().isEmpty()
                && !getAlipayPublicKey().isEmpty()
                && !getEncryptKey().isEmpty();
    }

    public boolean hasUsableNotifyUrl() {
        String url = getNotifyUrl();
        if (url.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return false;
        }
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return !isLoopbackHost(normalized);
    }

    public AlipayConfig toSdkConfig() {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(getGateway());
        config.setAppId(getAppId());
        config.setPrivateKey(getMerchantPrivateKey());
        config.setFormat(FORMAT);
        config.setCharset(CHARSET);
        config.setSignType(SIGN_TYPE);
        config.setAlipayPublicKey(getAlipayPublicKey());
        config.setEncryptKey(getEncryptKey());
        config.setEncryptType(ENCRYPT_TYPE);
        return config;
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "0.0.0.0".equals(host)
                || "::1".equals(host)
                || host.endsWith(".localhost");
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
