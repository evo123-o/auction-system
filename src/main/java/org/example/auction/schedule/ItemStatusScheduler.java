package org.example.auction.schedule;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.auction.entity.Item;
import org.example.auction.mapper.ItemMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ItemStatusScheduler {

    private final ItemMapper itemMapper;

    public ItemStatusScheduler(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    // 每30秒检查一次（按需调整为 cron）
    @Scheduled(fixedDelay = 30_000)
    public void flipStatuses() {
        LocalDateTime now = LocalDateTime.now();

        // 到了开始时间、且尚未结束的，置为 RUNNING
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .ne(Item::getStatus, "RUNNING")
                .le(Item::getStartTime, now)
                .gt(Item::getEndTime, now)
                .set(Item::getStatus, "RUNNING")
                .set(Item::getUpdatedAt, now));

        // 到了结束时间的，把 RUNNING 置为 CLOSED
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getStatus, "RUNNING")
                .le(Item::getEndTime, now)
                .set(Item::getStatus, "CLOSED")
                .set(Item::getUpdatedAt, now));
    }
}
