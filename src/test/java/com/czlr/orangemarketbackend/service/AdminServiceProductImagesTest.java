package com.czlr.orangemarketbackend.service;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import com.czlr.orangemarketbackend.entity.dto.AdminProductImagesRequest;
import com.czlr.orangemarketbackend.entity.dto.ProductDTO;
import com.czlr.orangemarketbackend.entity.po.Product;
import com.czlr.orangemarketbackend.mapper.OrderMapper;
import com.czlr.orangemarketbackend.mapper.ProductMapper;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import com.czlr.orangemarketbackend.service.oss.OssOwnedUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceProductImagesTest {

    private ProductMapper productMapper;
    private ProductService productService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        AliyunOssProperties properties = new AliyunOssProperties();
        ReflectionTestUtils.setField(properties, "publicBaseUrl", "https://cdn.example.com");
        ReflectionTestUtils.setField(properties, "host", "https://orange-market.oss-cn-hangzhou.aliyuncs.com");
        productMapper = mock(ProductMapper.class);
        productService = mock(ProductService.class);
        adminService = new AdminService(
                mock(OrderMapper.class),
                mock(UserAccountMapper.class),
                productMapper,
                productService,
                new OssOwnedUrlValidator(properties),
                new ObjectMapper(),
                mock(RedisTemplate.class));
    }

    @Test
    void rejectsExternalImageUrl() {
        Product product = new Product();
        product.setId(1L);
        when(productMapper.selectById(1L)).thenReturn(product);

        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.updateProductImages(
                1L, new AdminProductImagesRequest(null, List.of("https://evil.example/a.png"))));
        assertEquals(ResultCode.BAD_REQUEST, ex.getResultCode());
    }

    @Test
    void writesOwnedUrls() {
        Product product = new Product();
        product.setId(7L);
        when(productMapper.selectById(7L)).thenReturn(product);
        when(productService.toProductDTO(product)).thenReturn(new ProductDTO());

        String url = "https://cdn.example.com/products/20260919/a.png";
        adminService.updateProductImages(7L, new AdminProductImagesRequest(null, List.of(url)));

        assertEquals(url, product.getCoverImage());
        assertEquals("[\"https://cdn.example.com/products/20260919/a.png\"]", product.getImagesJson());
        verify(productMapper).updateById(product);
        verify(productService).invalidateProductCaches(7L);
        verify(productService).toProductDTO(any(Product.class));
        verify(productService).invalidateProductCaches(anyLong());
    }
}
