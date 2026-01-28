package org.example.auction.service.impl;

import org.example.auction.entity.Item;
import org.example.auction.service.ItemCacheService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 ConcurrentHashMap 的内存缓存实现
 * 支持5分钟自动过期策略
 */
@Service
public class ItemCacheServiceImpl implements ItemCacheService {

    /**
     * 缓存条目包装类
     */
    private static class CacheEntry {
        private final Item item;
        private final Instant expireAt;

        CacheEntry(Item item, long ttlMillis) {
            this.item = item;
            this.expireAt = Instant.now().plusMillis(ttlMillis);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expireAt);
        }

        Item getItem() {
            return item;
        }
    }

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    // 缓存统计
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    // 缓存过期时间（毫秒），默认5分钟
    @Value("${app.cache.item-ttl-ms:300000}")
    private long cacheTtlMs;

    /**
     * 创建 Item 的防御性拷贝
     */
    private Item copyItem(Item source) {
        if (source == null) return null;
        Item copy = new Item();
        BeanUtils.copyProperties(source, copy);
        return copy;
    }

    @Override
    public Item getCachedItem(Long itemId) {
        if (itemId == null) return null;

        CacheEntry entry = cache.get(itemId);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(itemId);
            misses.incrementAndGet();
            return null;
        }

        hits.incrementAndGet();
        // 返回防御性拷贝，防止外部修改缓存数据
        return copyItem(entry.getItem());
    }

    @Override
    public void cacheItem(Item item) {
        if (item == null || item.getId() == null) return;
        // 存储防御性拷贝，防止外部修改影响缓存
        cache.put(item.getId(), new CacheEntry(copyItem(item), cacheTtlMs));
    }

    @Override
    public void updateCachedPrice(Long itemId, BigDecimal newPrice) {
        if (itemId == null || newPrice == null) return;

        CacheEntry entry = cache.get(itemId);
        if (entry != null && !entry.isExpired()) {
            // 创建新的拷贝并更新价格，然后存入缓存
            Item updatedItem = copyItem(entry.getItem());
            updatedItem.setCurrentPrice(newPrice);
            // 重新放入以重置过期时间
            cache.put(itemId, new CacheEntry(updatedItem, cacheTtlMs));
        }
    }

    @Override
    public void evictItem(Long itemId) {
        if (itemId != null) {
            cache.remove(itemId);
        }
    }

    @Override
    public void clearAll() {
        cache.clear();
        hits.set(0);
        misses.set(0);
    }

    @Override
    public List<Item> getAllCachedItems() {
        List<Item> items = new ArrayList<>();
        for (Map.Entry<Long, CacheEntry> e : cache.entrySet()) {
            if (!e.getValue().isExpired()) {
                // 返回防御性拷贝
                items.add(copyItem(e.getValue().getItem()));
            }
        }
        return items;
    }

    @Override
    public CacheStats getStats() {
        return new CacheStats(cache.size(), hits.get(), misses.get());
    }

    /**
     * 定时清理过期缓存（每分钟执行一次）
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpired() {
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
