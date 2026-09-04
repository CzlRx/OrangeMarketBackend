package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPageDTO {
    private List<ProductDTO> list;
    private int total;
    private int page;
    private int pageSize;
    private boolean hasMore;
}
