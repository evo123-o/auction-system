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
        item.setStatus("RUNNING");
        // 若开始时间在未来，调整为当前；生产环境可改为记录真实开拍时间
        if (item.getStartTime() == null || item.getStartTime().isAfter(LocalDateTime.now())) {
            item.setStartTime(LocalDateTime.now());
        }
        item.setUpdatedAt(LocalDateTime.now());
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

}
