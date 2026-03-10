package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import org.example.auction.dto.CreateItemRequest;
import org.example.auction.entity.Item;
import org.example.auction.entity.User;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.ItemService;
import org.example.auction.storage.StorageService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * ItemService 实现（整合 StorageService）
 */
@Service
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    private final StorageService storageService;

    @Getter
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Getter
    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    @Value("${app.auction.credit-score.min-to-list:60}")
    private int minCreditScoreToList;

    public ItemServiceImpl(ItemMapper itemMapper, UserMapper userMapper, StorageService storageService) {
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.storageService = storageService;
    }

    @Override
    public IPage<Item> pageItems(Page<Item> page, String title, String category, String status) {
        LambdaQueryWrapper<Item> qw = new LambdaQueryWrapper<Item>()
                .like(title != null && !title.isBlank(), Item::getTitle, title)
                .eq(category != null && !category.isBlank(), Item::getCategory, category)
                .eq(status != null && !status.isBlank(), Item::getStatus, status)
                .orderByDesc(Item::getCreatedAt);
        return itemMapper.selectPage(page, qw);
    }

    @Override
    public Item getById(Long id) {
        return itemMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item startAuction(Long id) {
        Item item = itemMapper.selectById(id);
        if (item == null) throw new IllegalArgumentException("拍品不存在");

        // 增加安全检查：只有 "ON_SHELF" (已上架) 或 "CLOSED" (流拍/结束需重启) 的商品可以开启拍卖
        // 未审核通过(PENDING/REJECTED)的商品不能直接开拍
        String currentStatus = item.getStatus();
        if (!"ON_SHELF".equalsIgnoreCase(currentStatus) && !"CLOSED".equalsIgnoreCase(currentStatus)) {

            throw new IllegalStateException("商品状态为 [" + currentStatus + "]，无法开启拍卖，请先通过审核上架。");
        }

        LocalDateTime now = LocalDateTime.now();
        item.setStatus("RUNNING");
        item.setUpdatedAt(now);

        // 先计算原定持续时长（用于在需要延长结束时间时参考）
        long durationSeconds = 3600; // 默认 1 小时
        if (item.getStartTime() != null && item.getEndTime() != null) {
            long diff = java.time.Duration.between(item.getStartTime(), item.getEndTime()).getSeconds();
            if (diff > 0) {
                durationSeconds = diff;
            }
        }

        // 如果开始时间在未来，或者我们要重新开启一个已结束的拍卖，
        // 将开始时间设为当前时间是合理的。
        item.setStartTime(now);

        // 如果结束时间已经过期（早于当前），则必须延长，否则会被定时任务立即关闭
        if (item.getEndTime() == null || item.getEndTime().isBefore(now)) {
            item.setEndTime(now.plusSeconds(durationSeconds));
        }

        itemMapper.updateById(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item create(CreateItemRequest req, Long createdBy) {

        // Check credit score
        User user = userMapper.selectById(createdBy);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        int currentScore = user.getCreditScore() != null ? user.getCreditScore() : 100;
        if (currentScore < minCreditScoreToList) {
            throw new IllegalArgumentException("您的信用分过低 (" + currentScore + " < " + minCreditScoreToList + ")，无法发布拍品！");
        }

        Item item = new Item();
        BeanUtils.copyProperties(req, item);
        // 先提交审核：创建阶段不写入拍卖时间，避免未审核先开拍
        item.setStartTime(null);
        item.setEndTime(null);
        if (item.getAutoExtension() == null) {
            item.setAutoExtension(Boolean.FALSE);
        }
        item.setCreatedBy(createdBy);
        item.setCreatedAt(LocalDateTime.now());
        // 默认状态可设为 PENDING
        if (item.getStatus() == null) item.setStatus("PENDING");
        itemMapper.insert(item);
        // 初始当前价为起拍价
        item.setCurrentPrice(item.getStartPrice());
        itemMapper.updateById(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item update(Long id, CreateItemRequest req) {
        Item item = itemMapper.selectById(id);
        if (item == null) throw new IllegalArgumentException("拍品不存在");

        String statusBefore = item.getStatus();
        Boolean oldAutoExtension = item.getAutoExtension();
        BeanUtils.copyProperties(req, item);
        if (req.getAutoExtension() == null) {
            item.setAutoExtension(oldAutoExtension);
        }

        // 仅在审核通过后（ON_SHELF）允许设置拍卖时间
        boolean touchingSchedule = req.getStartTime() != null || req.getEndTime() != null;
        if (touchingSchedule && !"ON_SHELF".equalsIgnoreCase(statusBefore)) {
            throw new IllegalStateException("请先通过审核，再设置拍卖时间");
        }

        if (touchingSchedule) {
            if (req.getStartTime() == null || req.getEndTime() == null) {
                throw new IllegalArgumentException("开始时间和结束时间需同时设置");
            }
            if (!req.getEndTime().isAfter(req.getStartTime())) {
                throw new IllegalArgumentException("结束时间必须晚于开始时间");
            }
        }

        item.setUpdatedAt(LocalDateTime.now());

        // 如果之前被拒绝，用户修改后重新变为待审核
        if ("REJECTED".equalsIgnoreCase(item.getStatus())) {
            item.setStatus("PENDING");
        }

        itemMapper.updateById(item);
        return item;
    }


    /**
     * 保存图片（通过 StorageService），并更新 item.imagePath
     */
    @Override
    @Transactional
    public String saveImage(Long itemId, MultipartFile file) throws IOException {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new IllegalArgumentException("item not found: " + itemId);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("empty file");
        }
        String folder = "items/" + itemId;
        String imageUrl = storageService.store(file, folder);
        item.setImagePath(imageUrl);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return imageUrl;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item updateImagePath(Long id, String imageUrl) {
        Item item = itemMapper.selectById(id);
        if (item == null) throw new IllegalArgumentException("拍品不存在");
        item.setImagePath(imageUrl);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return item;
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        Item item = itemMapper.selectById(id);
        if (item == null) return false;

        // 增加保护：正在拍卖或已售出的商品未必允许直接物理删除
        if ("RUNNING".equalsIgnoreCase(item.getStatus())) {
            throw new IllegalStateException("正在拍卖中的商品无法删除");
        }

        // 尝试删除关联文件（如果 StorageService 支持）
        String imagePath = item.getImagePath();
        try {
            if (imagePath != null && !imagePath.isBlank()) {
                storageService.delete(imagePath);
            }
        } catch (Exception ignored) {
            // 删除文件失败不应该阻止删除数据；记录日志可选
        }
        int rows = itemMapper.deleteById(id);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item audit(Long id, boolean approved, String reason) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("拍品不存在");
        }

        // 审核逻辑：允许对非终态商品进行干预
        // 1. PENDING (待审核) -> 可通过/可驳回
        // 2. REJECTED (已驳回) -> 可重新通过 (纠正错误)
        // 3. ON_SHELF (已上架) -> 可强制驳回 (违规下架)
        String s = item.getStatus();
        if ("RUNNING".equalsIgnoreCase(s) || "SOLD".equalsIgnoreCase(s) || "CLOSED".equalsIgnoreCase(s)) {
            // 已经进入交易流程或结束的，不允许通过简单的审核接口修改状态
            throw new IllegalStateException("当前状态 [" + s + "] 不再支持审核操作");
        }

        if (approved) {
            // 审核通过后先上架，等待卖家自行设置开拍时间
            item.setStatus("ON_SHELF");
            item.setStartTime(null);
            item.setEndTime(null);
            // 清理拒绝原因
            item.setRejectReason(null);
        } else {
            item.setStatus("REJECTED");
            // 保存拒绝原因（便于后续展示与审计）
            item.setRejectReason(reason == null ? null : reason.trim());
        }
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item stopAuction(Long id) {
        Item item = itemMapper.selectById(id);
        if (item == null) throw new IllegalArgumentException("拍品不存在");

        String s = item.getStatus();
        // 只在 RUNNING 状态下允许结束拍卖
        if (!"RUNNING".equalsIgnoreCase(s)) {
            throw new IllegalStateException("只有进行中的拍品才能停止拍卖，目前状态: " + s);
        }

        item.setStatus("CLOSED");
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return item;
    }

}
