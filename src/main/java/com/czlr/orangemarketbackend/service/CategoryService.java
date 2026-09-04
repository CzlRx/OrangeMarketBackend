package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czlr.orangemarketbackend.entity.po.ProductCategory;
import com.czlr.orangemarketbackend.mapper.ProductCategoryMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
public class CategoryService extends ServiceImpl<ProductCategoryMapper, ProductCategory> {

    private static final String CATEGORY_CACHE_KEY = "cache:categories:active:v1";

private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CategoryService(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<ProductCategory> getCategories() {

        Object cachedValue = redisTemplate.opsForValue().get(CATEGORY_CACHE_KEY);

        if(cachedValue != null) {
            return objectMapper.convertValue(cachedValue, new TypeReference<List<ProductCategory>>() {});
        }
        /*if (cachedValue instanceof List<?> cachedCategories) {
            return cachedCategories.stream()
                    .map(category -> category instanceof ProductCategory productCategory
                            ? productCategory
                            : objectMapper.convertValue(category, ProductCategory.class))
                    .toList();
        }*/

        List<ProductCategory> list = list();
        redisTemplate.opsForValue().set(CATEGORY_CACHE_KEY, list, 60, TimeUnit.MINUTES);
        return list;
    }
}
