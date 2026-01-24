package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.auction.entity.Bid;

import java.util.List;

@Mapper
public interface BidMapper extends BaseMapper<Bid> {

    @Select("SELECT * FROM bids WHERE item_id = #{itemId} ORDER BY amount DESC, bid_time LIMIT 1")
    Bid findTopByItem(Long itemId);

    @Select("SELECT * FROM bids WHERE item_id = #{itemId} ORDER BY amount DESC")
    List<Bid> listByItem(Long itemId);
}
