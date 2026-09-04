package com.czlr.orangemarketbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czlr.orangemarketbackend.entity.po.ProductCategory;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {
}
