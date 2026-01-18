package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.auction.entity.Item;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {

}
