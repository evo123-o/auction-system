package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.auction.dto.CreateItemRequest;
import org.example.auction.entity.Item;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.service.ItemService;
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
 * ItemService 实现
 */
@Service
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    public ItemServiceImpl(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
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
        // 确保数值默认
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
        // 这里只覆盖部分字段；也可用 UpdateWrapper 更细粒度控制
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

        // 确保目录存在
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID() + ext;
        File dest = new File(dir, filename);
        file.transferTo(dest);

        // 更新 item 的 imagePath（保存为相对路径或 URL）
        String imagePath = uploadBaseUrl + "/" + filename;
        item.setImagePath(imagePath);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        return imagePath;
    }
    //新增 updateImagePath 实现并保留原 saveImage 实现
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
}
