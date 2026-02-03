package org.example.auction.service;

import org.example.auction.entity.Item;

import java.math.BigDecimal;
import java.util.List;

/**
 * 热门拍品缓存服务
 * 使用 ConcurrentHashMap 实现内存级缓存
 */
public interface ItemCacheService {

    /**
     * 获取缓存的拍品信息，如果缓存不存在或已过期则返回 null
     */
    Item getCachedItem(Long itemId);

    /**
     * 将拍品信息放入缓存
     */
    void cacheItem(Item item);

    /**
     * 更新缓存中拍品的当前价格
     */
    void updateCachedPrice(Long itemId, BigDecimal newPrice);

    /**
     * 从缓存中移除拍品
     */
    void evictItem(Long itemId);

    /**
     * 清空所有缓存
     */
    void clearAll();

    /**
     * 获取所有缓存的热门拍品
     */
    List<Item> getAllCachedItems();

    /**
     * 获取缓存统计信息
     */
    CacheStats getStats();

    /**
     * 缓存统计信息
     */
    record CacheStats(int size, long hits, long misses) {
        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
