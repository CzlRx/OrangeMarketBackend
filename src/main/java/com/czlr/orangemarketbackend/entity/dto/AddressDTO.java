package com.czlr.orangemarketbackend.entity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressDTO {
    private String id;
    private String receiver;
    private String phone;
    private String provinceCode;
    private String province;
    private String cityCode;
    private String city;
    private String districtCode;
    private String district;
    private String detail;

    @JsonProperty("isDefault")
    private Boolean isDefault;
}
