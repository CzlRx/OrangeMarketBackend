package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewItemRequest {
    private String orderItemId;
    private String productId;
    private Integer rating;
    private String content;
    private Boolean anonymous;
}
