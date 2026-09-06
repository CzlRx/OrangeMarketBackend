package com.czlr.orangemarketbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czlr.orangemarketbackend.entity.po.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @Update("UPDATE product SET stock = stock - #{quantity} "
            + "WHERE id = #{productId} AND status = 'on_sale' "
            + "AND stock >= #{quantity} AND deleted_at = 0")
    void decreaseStock(@Param("productId") String productId, @Param("quantity") Integer quantity);

    @Update("UPDATE product SET stock = stock + #{quantity} "
            + "WHERE id = #{productId} AND deleted_at = 0")
    int increaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
