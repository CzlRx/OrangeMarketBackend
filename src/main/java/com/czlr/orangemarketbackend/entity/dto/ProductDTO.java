package com.czlr.orangemarketbackend.entity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDTO {
    private String id;
    private String name;
    private String subtitle;
    private String categoryId;
    private List<String> images;
    private String videoUrl;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer sales;
    private Double rating;
    private Integer reviewCount;
    private String description;
    private BigDecimal shippingFee;
    private List<String> tags;
}
