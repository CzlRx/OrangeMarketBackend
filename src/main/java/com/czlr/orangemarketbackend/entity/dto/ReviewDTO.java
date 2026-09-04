package com.czlr.orangemarketbackend.entity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewDTO {
    private String id;
    private String productId;
    private String userName;
    private String avatar;
    private String content;
    private Integer rating;
    private Boolean anonymous;
    private LocalDateTime createdAt;
}
