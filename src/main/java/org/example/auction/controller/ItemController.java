package org.example.auction.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.example.auction.dto.CreateItemRequest;
import org.example.auction.dto.ItemDTO;
import org.example.auction.dto.PageResponse;
import org.example.auction.entity.Item;
import org.example.auction.service.ItemService;
import org.example.auction.service.UserService;
import org.example.auction.storage.StorageService;
import org.example.auction.utils.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ItemController：带基于当前登录用户的权限校验、标准分页 DTO、图片上传通过 StorageService
 */
@RestController
@RequestMapping("/api/items")
@Validated
public class ItemController {

    private final ItemService itemService;
    private final StorageService storageService;
    private final UserService userService;

    @Value("${image.max-size-bytes:2097152}")
    private long maxImageSize;

    public ItemController(ItemService itemService, StorageService storageService, UserService userService) {
        this.itemService = itemService;
        this.storageService = storageService;
        this.userService = userService;
    }

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
        long total = results.getTotal();
        long pages = (total + size - 1) / size;
        PageResponse<ItemDTO> resp = PageResponse.<ItemDTO>builder()
                .total(total)
                .pages(pages)
                .current(page)
                .size(size)
                .records(dtoList)
                .build();
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        Item item = itemService.getById(id);
        if (item == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("item not found");
        return ResponseEntity.ok(toDto(item));
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateItemRequest req) {
        Optional<Long> optId = SecurityUtils.getCurrentUserId(userService);
        if (optId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        Item created = itemService.create(req, optId.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody CreateItemRequest req) {
        Optional<Long> optId = SecurityUtils.getCurrentUserId(userService);
        if (optId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        Item existing = itemService.getById(id);
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("item not found");
        // 仅创建者或 ADMIN 能更新
        if (!existing.getCreatedBy().equals(optId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限修改该拍品");
        }
        Item updated = itemService.update(id, req);
        return ResponseEntity.ok(toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Optional<Long> optId = SecurityUtils.getCurrentUserId(userService);
        if (optId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");

        Item existing = itemService.getById(id);
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("item not found");
        // 仅创建者或 ADMIN 能删除
        if (!existing.getCreatedBy().equals(optId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限删除该拍品");
        }

        boolean ok = itemService.deleteById(id);
        if (ok) return ResponseEntity.noContent().build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除失败");
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<?> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Optional<Long> optId = SecurityUtils.getCurrentUserId(userService);
        if (optId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");

        Item existing = itemService.getById(id);
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("item not found");
        // 仅创建者或 ADMIN 可以上传图片
        if (!existing.getCreatedBy().equals(optId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限为该拍品上传图片");
        }

        // 前置校验：文件大小超过阈值直接返回 413（LocalStorageService 会尝试压缩，但这里预防过大的原始文件）
        if (file.getSize() > maxImageSize * 5) { // 防止上传极大文件（5x maxSize）
            return ResponseEntity.status(413).body(java.util.Map.of("error", "上传文件过大"));
        }

        try {
            String imageUrl = itemService.saveImage(id, file);
            Item item = itemService.updateImagePath(id, imageUrl);
            return ResponseEntity.ok().body(java.util.Map.of("imageUrl", imageUrl, "item", toDto(item)));
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
        dto.setImageUrl(item.getImagePath());
        return dto;
    }
}