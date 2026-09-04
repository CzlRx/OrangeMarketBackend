package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.CategoryDTO;
import com.czlr.orangemarketbackend.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<CategoryDTO>> getCategories() {
        List<CategoryDTO> list = categoryService.getCategories().stream()
                .map(category -> new CategoryDTO(
                        category.getId() == null ? null : category.getId().toString(),
                        category.getName(),
                        category.getEyebrow(),
                        category.getColor(),
                        category.getIconKey(),
                        category.getIsVirtual() != null && category.getIsVirtual() != 0
                ))
                .toList();
        return Result.success(list);
    }
}
