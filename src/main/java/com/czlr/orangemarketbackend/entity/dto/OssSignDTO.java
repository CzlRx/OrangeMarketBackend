package com.czlr.orangemarketbackend.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OssSignDTO {
    private String dir;
    private String key;
    private String host;
    private String accessUrl;
    private String policy;

    @JsonProperty("security_token")
    private String securityToken;

    private String signature;

    @JsonProperty("x_oss_credential")
    private String xOssCredential;

    @JsonProperty("x_oss_date")
    private String xOssDate;

    @JsonProperty("x_oss_signature_version")
    private String xOssSignatureVersion;

    private String callback;
}
