package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.auction.entity.BreachRecord;

import java.util.List;

@Mapper
public interface BreachRecordMapper extends BaseMapper<BreachRecord> {

    @Select("SELECT * FROM breach_records WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<BreachRecord> listByUser(Long userId);
}
