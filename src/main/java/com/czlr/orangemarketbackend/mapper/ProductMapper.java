package com.czlr.orangemarketbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czlr.orangemarketbackend.entity.po.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

}
