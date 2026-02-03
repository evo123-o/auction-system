package org.example.auction.service;

import lombok.NonNull;
import org.example.auction.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bid 服务接口
 */
public interface BidService {
    /**
     * 对商品出价。
     * 此方法是事务性的，并且将会
     * - 锁定商品行（SELECT ... FOR UPDATE），
     * - 验证业务规则（商品状态，出价金额 > 当前价格，用户 != 拥有者），
     * - 插入一条出价记录（Bid），
     * - 更新商品的当前价格。
     *
     * @param userId 出价用户的ID
     * @param itemId 商品的ID
     * @param amount 出价金额（必须大于当前价格）
     * @return 保存的出价记录（Bid）
     * @throws IllegalArgumentException 当违反业务规则时
     */
    Bid placeBid(Long userId, Long itemId, BigDecimal amount);

    /**
     * 查询某拍品的所有出价记录
     */
    List<Bid> listByItem(Long itemId);

    // ===== 管理员接口 =====

    /**
     * 分页查询所有出价记录
     */
    Page<@NonNull Bid> pageAll(Pageable pageable);

    /**
     * 撤销出价（管理员操作，若撤销的是最高价需回滚价格）
     */
    void cancelBid(Long bidId);
}
