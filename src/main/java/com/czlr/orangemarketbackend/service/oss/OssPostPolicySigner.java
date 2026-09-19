package com.czlr.orangemarketbackend.service.oss;

import com.czlr.orangemarketbackend.entity.dto.OssSignDTO;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 构造 PostPolicy 并用 STS 临时密钥计算 OSS4 签名。
 */
public final class OssPostPolicySigner {

    public static final String SIGNATURE_VERSION = "OSS4-HMAC-SHA256";
    public static final String SUCCESS_ACTION_STATUS = "200";
    public static final String CALLBACK_BODY =
            "bucket=${bucket}&object=${object}&size=${size}&mimeType=${mimeType}"
                    + "&scene=${x:scene}&userId=${x:userId}";
    public static final String CALLBACK_BODY_TYPE = "application/x-www-form-urlencoded";

    private static final DateTimeFormatter CREDENTIAL_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter OSS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter EXPIRATION =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OssPostPolicySigner() {
    }

    public static OssSignDTO sign(OssPostPolicyCommand command) {
        ZonedDateTime now = command.now().atZone(ZoneOffset.UTC);
        String date = now.format(CREDENTIAL_DATE);
        String xOssDate = now.format(OSS_DATE);
        String expiration = now.plusSeconds(command.expireSeconds()).format(EXPIRATION);
        String xOssCredential = command.credentials().accessKeyId()
                + "/" + date + "/" + command.region() + "/oss/aliyun_v4_request";

        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("callbackUrl", command.callbackUrl());
        callback.put("callbackBody", CALLBACK_BODY);
        callback.put("callbackBodyType", CALLBACK_BODY_TYPE);
        String callbackBase64 = Base64.getEncoder()
                .encodeToString(OBJECT_MAPPER.writeValueAsString(callback).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("expiration", expiration);
        List<Object> conditions = new ArrayList<>();
        conditions.add(Map.of("bucket", command.bucket()));
        conditions.add(Map.of("x-oss-security-token", command.credentials().securityToken()));
        conditions.add(Map.of("x-oss-signature-version", SIGNATURE_VERSION));
        conditions.add(Map.of("x-oss-credential", xOssCredential));
        conditions.add(Map.of("x-oss-date", xOssDate));
        conditions.add(List.of("content-length-range", 1, command.maxBytes()));
        conditions.add(List.of("eq", "$success_action_status", SUCCESS_ACTION_STATUS));
        conditions.add(List.of("eq", "$key", command.key()));
        conditions.add(List.of("eq", "$content-type", command.contentType()));
        conditions.add(Map.of("x:scene", command.scene()));
        conditions.add(Map.of("x:userId", command.userId()));
        policy.put("conditions", conditions);

        String jsonPolicy = OBJECT_MAPPER.writeValueAsString(policy);
        String stringToSign = Base64.getEncoder().encodeToString(jsonPolicy.getBytes(StandardCharsets.UTF_8));
        String signature = OssSignatureV4.signPolicy(
                command.credentials().accessKeySecret(),
                date,
                command.region(),
                stringToSign);

        OssSignDTO dto = new OssSignDTO();
        dto.setDir(command.dir());
        dto.setKey(command.key());
        dto.setHost(command.host());
        dto.setAccessUrl(command.accessUrl());
        dto.setPolicy(stringToSign);
        dto.setSecurityToken(command.credentials().securityToken());
        dto.setSignature(signature);
        dto.setXOssCredential(xOssCredential);
        dto.setXOssDate(xOssDate);
        dto.setXOssSignatureVersion(SIGNATURE_VERSION);
        dto.setCallback(callbackBase64);
        return dto;
    }

    public record OssPostPolicyCommand(
            OssStsCredentials credentials,
            String bucket,
            String region,
            String host,
            String accessUrl,
            String callbackUrl,
            String dir,
            String key,
            String contentType,
            String scene,
            String userId,
            long expireSeconds,
            int maxBytes,
            Instant now) {
    }
}
