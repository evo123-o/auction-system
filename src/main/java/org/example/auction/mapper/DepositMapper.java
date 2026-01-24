package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.auction.entity.Deposit;

@Mapper
public interface DepositMapper extends BaseMapper<Deposit> {

    @Select("SELECT * FROM deposits WHERE item_id = #{itemId} AND user_id = #{userId} LIMIT 1")
    Deposit findByItemAndUser(Long itemId, Long userId);
}
