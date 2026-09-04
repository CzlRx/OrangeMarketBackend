package com.czlr.orangemarketbackend.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CategoryDTO {
    private String id;
    private String name;
    private String eyebrow;
    private String color;
    private String icon;

    @JsonProperty("isVirtual")
    private boolean virtual;
}
