package org.example.auction.service;

import org.example.auction.entity.Item;
import org.example.auction.entity.Bid;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.mapper.BidMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 伪并发测试：多个线程同时对同一 item 出价，断言最后的当前价是最高地出价。
 * 注意：这是一个集成测试，需要真实数据库（例如测试容器或开发 DB）。
 * 每个线程内部会调用 Service 的 placeBid（带事务），通过 DB 的行锁实现并发安全。
 */
@SpringBootTest
public class BidServiceConcurrencyTest {

    @Autowired
    private BidService bidService;

    @Autowired
    private ItemMapper itemMapper;

    @Autowired
    private BidMapper bidMapper;

    private Long itemId;
    private final Long bidderBase = 200L;

    @BeforeEach
    public void setup() {
        // create a fresh item
        Item it = new Item();
        it.setTitle("Concurrency Test Item");
        it.setCategory("Test");
        it.setDescription("for concurrency");
        it.setStartPrice(new BigDecimal("100.00"));
        it.setCurrentPrice(new BigDecimal("100.00"));
        it.setDepositAmount(new BigDecimal("0"));
        it.setStartTime(LocalDateTime.now().minusMinutes(10));
        it.setEndTime(LocalDateTime.now().plusMinutes(10));
        it.setStatus("RUNNING");
        it.setExtendCount(0);
        it.setMaxExtend(3);
        // sample seller
        Long sellerId = 1L;
        it.setCreatedBy(sellerId);
        it.setCreatedAt(LocalDateTime.now());
        itemMapper.insert(it);
        this.itemId = it.getId();

        // clean any old bids
        bidMapper.delete(null);
    }

    @Test
    public void testConcurrentBids() throws InterruptedException, ExecutionException {
        int threads = 10;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Bid>> futures = new ArrayList<>();

        // Each thread will try to bid a higher price
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            Callable<Bid> task = () -> {
                ready.countDown();
                start.await(); // wait for all to be ready
                Long bidderId = bidderBase + idx;
                BigDecimal bidAmount = new BigDecimal("100").add(new BigDecimal(idx + 1).multiply(new BigDecimal("10"))); // 110,120,...
                try {
                    return bidService.placeBid(bidderId, itemId, bidAmount);
                } catch (Exception ex) {
                    // for test, we just return null on failure (e.g., bid too low)
                    return null;
                }
            };
            futures.add(exec.submit(task));
        }

        // wait all ready, then start
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        // collect results
        List<Bid> success = new ArrayList<>();
        for (Future<Bid> f : futures) {
            Bid b = f.get();
            if (b != null) success.add(b);
        }

        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);

        // fetch item and assert current price equals highest successful bid
        Item item = itemMapper.selectById(itemId);
        BigDecimal current = item.getCurrentPrice();
        BigDecimal expectedMax = success.stream()
                .map(Bid::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(new BigDecimal("100.00"));

        Assertions.assertEquals(0, current.compareTo(expectedMax),
                "item currentPrice should equal highest successful bid");

        System.out.println("successful bids: " + success.size() + ", final price: " + current);
    }
}