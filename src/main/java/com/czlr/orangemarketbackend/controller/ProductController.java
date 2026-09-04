package com.czlr.orangemarketbackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.ProductDTO;
import com.czlr.orangemarketbackend.entity.dto.ProductDetailDTO;
import com.czlr.orangemarketbackend.entity.dto.ProductPageDTO;
import com.czlr.orangemarketbackend.entity.dto.ProductReviewPageDTO;
import com.czlr.orangemarketbackend.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Result<ProductPageDTO> getProducts(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "12") int pageSize,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "sort", defaultValue = "default") String sort) {

        Page<ProductDTO> productPage = productService.getProducts(
                new Page<>(page, pageSize), keyword, categoryId, sort);
        ProductPageDTO productPageDTO = new ProductPageDTO(
                productPage.getRecords(),
                Math.toIntExact(productPage.getTotal()),
                (int) productPage.getCurrent(),
                (int) productPage.getSize(),
                productPage.hasNext()
        );
        return Result.success(productPageDTO);
    }

    @GetMapping("/{productId}")
    public Result<ProductDetailDTO> getProductDetail(@PathVariable Long productId) {
        return Result.success(productService.getProductDetail(productId));
    }

    @GetMapping("/{productId}/reviews")
    public Result<ProductReviewPageDTO> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "sort", defaultValue = "latest") String sort) {
        return Result.success(productService.getProductReviews(
                productId, filter, page, pageSize, sort));
    }
}
