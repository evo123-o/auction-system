package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import org.example.auction.dto.CreateItemRequest;
import org.example.auction.entity.Item;
import org.example.auction.mapper.ItemMapper;
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
    private final StorageService storageService;

    @Getter
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Getter
    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    public ItemServiceImpl(ItemMapper itemMapper, StorageService storageService) {
        this.itemMapper = itemMapper;
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
        Item item = new Item();
        BeanUtils.copyProperties(req, item);
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

        BeanUtils.copyProperties(req, item);
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
        // 仅 PENDING 状态可审核，或者 REJECTED 状态也可以重新审核通过?
        // 通常只审核 PENDING。
        if (!"PENDING".equalsIgnoreCase(item.getStatus())) {
             // 允许管理员把 REJECTED 改回 ON_SHELF? 或者是 ON_SHELF 改回 REJECTED?
             // 这里做严格限制：只有 PENDING 可以操作。
            throw new IllegalStateException("当前状态不支持审核: " + item.getStatus());
        }

        if (approved) {
            item.setStatus("ON_SHELF");
        } else {
            item.setStatus("REJECTED");
            // TODO: 如果需要保存 reason，需要修改数据库添加 reason 字段
        }
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return item;
    }

}
