package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.auction.entity.Bid;

/**
 * MyBatis-Plus Mapper for Bid
 */
@Mapper
public interface BidMapper extends BaseMapper<Bid> {
    // extend BaseMapper for basic CRUD
}
