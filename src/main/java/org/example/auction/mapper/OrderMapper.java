package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.auction.dto.CategoryStatsDto;
import org.example.auction.entity.Order;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM orders WHERE status = 'PENDING_PAYMENT' AND pay_by < NOW()")
    List<Order> findOverdueUnpaid();

    @Select("SELECT i.category as name, COUNT(o.id) as count " +
            "FROM orders o " +
            "LEFT JOIN items i ON o.item_id = i.id " +
            "WHERE o.status = 'PAID' " +
            "GROUP BY i.category")
    List<CategoryStatsDto> countOrdersByCategory();

    @Select("SELECT IFNULL(SUM(final_price), 0) FROM orders WHERE status = 'PAID'")
    BigDecimal sumTotalVolume();

    @Select("SELECT COUNT(*) FROM orders WHERE status = 'PAID'")
    Long countTotalTransactions();

    /**
     * 查找已支付但发货超时（创建时间早于 now - hours）的订单
     * 这里使用 created_at 作为基准：如果需要更精确的发货截止时间，请在 Order 表增加 ship_by 字段并改用该字段。
     */
    @Select("SELECT * FROM orders WHERE status = 'PAID' AND created_at <= DATE_SUB(NOW(), INTERVAL #{hours} HOUR)")
    List<Order> findOverdueToShip(@Param("hours") int hours);
}
