package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.auction.entity.Evaluation;

import java.util.List;

@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    @Select("SELECT * FROM evaluations WHERE order_id = #{orderId}")
    List<Evaluation> findByOrderId(Long orderId);

    @Select("SELECT * FROM evaluations WHERE reviewer_id = #{reviewerId} ORDER BY created_at DESC")
    List<Evaluation> findByReviewerId(Long reviewerId);
}
