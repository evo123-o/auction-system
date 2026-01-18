package org.example.auction.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.example.auction.dto.CreateItemRequest;
import org.example.auction.dto.ItemDTO;
import org.example.auction.entity.Item;
import org.example.auction.service.ItemService;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ItemController：分页、详情、创建、图片上传示例
 */
@RestController
@RequestMapping("/api/items")
@Validated
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * 分页查询
     * GET /api/items?page=1&size=10&title=foo&category=bar&status=RUNNING
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status
    ) {
        Page<Item> pg = new Page<>(page, size);
        IPage<Item> results = itemService.pageItems(pg, title, category, status);
        List<ItemDTO> dtoList = results.getRecords().stream().map(this::toDto).collect(Collectors.toList());
        // 保留分页信息
        return ResponseEntity.ok().body(new org.springframework.data.domain.PageImpl<>(dtoList,
                org.springframework.data.domain.PageRequest.of(page - 1, size),
                results.getTotal()));
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        Item item = itemService.getById(id);
        if (item == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("item not found");
        }
        return ResponseEntity.ok(toDto(item));
    }

    /**
     * 创建拍品（不包含图片）
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateItemRequest req) {
        // 这里示例未集成认证，将 createdBy 写为 null 或者从 SecurityContext 获取当前用户 id
        Item created = itemService.create(req, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    /**
     * 上传图片并关联到拍品
     * POST /api/items/{id}/image  Content-Type: multipart/form-data
     */
    @PostMapping("/{id}/image")
    public ResponseEntity<?> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = itemService.saveImage(id, file);
            return ResponseEntity.ok().body(java.util.Map.of("imageUrl", imageUrl));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of("error", ex.getMessage()));
        }
    }

    private ItemDTO toDto(Item item) {
        if (item == null) return null;
        ItemDTO dto = ItemDTO.builder().build();
        BeanUtils.copyProperties(item, dto);
        // 将数据库的 image_path 映射到 DTO 的 imageUrl
        dto.setImageUrl(item.getImagePath());
        return dto;
    }
}
