package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.auction.entity.Logistics;

@Mapper
public interface LogisticsMapper extends BaseMapper<Logistics> {

    @Select("SELECT * FROM logistics WHERE order_id = #{orderId} LIMIT 1")
    Logistics findByOrderId(Long orderId);
}
