package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.auction.entity.Item;

/**
 * Item mapper with FOR UPDATE helper
 */
@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    /**
     * Select item by id using SELECT ... FOR UPDATE to acquire row lock within transaction.
     * Works with InnoDB.
     */
    @Select("SELECT id, title, category, description, start_price, current_price, deposit_amount, start_time, end_time, status, extend_count, max_extend, image_path, created_by, created_at, updated_at FROM items WHERE id = #{id} FOR UPDATE")
    Item selectByIdForUpdate(Long id);
}
