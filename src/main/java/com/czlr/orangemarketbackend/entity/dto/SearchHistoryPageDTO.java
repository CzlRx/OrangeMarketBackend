package com.czlr.orangemarketbackend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryPageDTO {
    private List<SearchHistoryDTO> list;
    private int total;
    private int page;
    private int pageSize;
    private boolean hasMore;
}
