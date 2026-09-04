package com.czlr.orangemarketbackend.entity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewSummaryDTO {
    private Double average;
    private Integer reviewCount;
    private Double goodRate;
    private Integer allCount;
    private Integer goodCount;
    private Integer mediumCount;
    private Integer badCount;
}
