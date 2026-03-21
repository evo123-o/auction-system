package org.example.auction.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.auction.entity.Evaluation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    @Select("SELECT * FROM evaluations WHERE order_id = #{orderId}")
    List<Evaluation> findByOrderId(Long orderId);

    @Select("SELECT * FROM evaluations WHERE reviewer_id = #{reviewerId} ORDER BY created_at DESC")
    List<Evaluation> findByReviewerId(Long reviewerId);

    @Select("SELECT e.* FROM evaluations e INNER JOIN orders o ON e.order_id = o.id WHERE o.item_id = #{itemId} ORDER BY e.created_at DESC")
    List<Evaluation> findByItemId(Long itemId);
}
