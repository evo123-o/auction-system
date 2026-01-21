package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ItemService 实现（整合 StorageService）
 */
@Service
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;
    private final StorageService storageService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    public ItemServiceImpl(ItemMapper itemMapper, StorageService storageService) {
        this.itemMapper = itemMapper;
        this.storageService = storageService;
    }

    @Override
    public IPage<Item> pageItems(Page<Item> page, String title, String category, String status) {
        LambdaQueryWrapper<Item> qw = new LambdaQueryWrapper<>();
        if (title != null && !title.isBlank()) {
            qw.like(Item::getTitle, title);
        }
        if (category != null && !category.isBlank()) {
            qw.eq(Item::getCategory, category);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(Item::getStatus, status);
        }
        qw.orderByDesc(Item::getCreatedAt);
        return itemMapper.selectPage(page, qw);
    }

    @Override
    public Item getById(Long id) {
        return itemMapper.selectById(id);
    }

    @Override
    @Transactional
    public Item create(CreateItemRequest req, Long createdBy) {
        Item item = new Item();
        BeanUtils.copyProperties(req, item);
        if (item.getStartPrice() == null) item.setStartPrice(BigDecimal.ZERO);
        if (item.getCurrentPrice() == null) item.setCurrentPrice(item.getStartPrice());
        if (item.getDepositAmount() == null) item.setDepositAmount(BigDecimal.ZERO);
        item.setStatus("PENDING");
        item.setExtendCount(0);
        item.setMaxExtend(3);
        item.setCreatedBy(createdBy);
        item.setCreatedAt(LocalDateTime.now());
        itemMapper.insert(item);
        return item;
    }

    @Override
    @Transactional
    public Item update(Long id, CreateItemRequest req) {
        Item existing = itemMapper.selectById(id);
        if (existing == null) {
            return null;
        }
        existing.setTitle(req.getTitle());
        existing.setCategory(req.getCategory());
        existing.setDescription(req.getDescription());
        if (req.getStartPrice() != null) existing.setStartPrice(req.getStartPrice());
        if (req.getDepositAmount() != null) existing.setDepositAmount(req.getDepositAmount());
        existing.setStartTime(req.getStartTime());
        existing.setEndTime(req.getEndTime());
        existing.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(existing);
        return existing;
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
    @Transactional
    public Item updateImagePath(Long itemId, String imageUrl) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) return null;
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
}
