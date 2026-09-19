package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HttpOssCallbackPublicKeyProvider implements OssCallbackPublicKeyProvider {

    private static final int MAX_BODY_BYTES = 16 * 1024;

    private final HttpClient httpClient;
    private final ConcurrentHashMap<URI, PublicKey> cache = new ConcurrentHashMap<>();

    public HttpOssCallbackPublicKeyProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    HttpOssCallbackPublicKeyProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public PublicKey getPublicKey(URI url) {
        PublicKey cached = cache.get(url);
        if (cached != null) {
            return cached;
        }
        PublicKey fetched = fetch(url);
        cache.put(url, fetched);
        return fetched;
    }

    private PublicKey fetch(URI url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(url)
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
            }
            byte[] body = response.body();
            if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
            }
            return parsePem(new String(body, StandardCharsets.US_ASCII));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
    }

    static PublicKey parsePem(String pem) {
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(normalized);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OSS 回调验签失败");
        }
    }
}
