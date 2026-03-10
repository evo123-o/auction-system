package org.example.auction.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Getter;
import org.example.auction.dto.CreateItemRequest;
import org.example.auction.dto.ItemDTO;
import org.example.auction.dto.PageResponse;
import org.example.auction.entity.Item;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.ItemService;
import org.example.auction.service.UserService;
import org.example.auction.storage.StorageService;
import org.example.auction.util.SecurityUtils;
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
 * 已改为使用 CurrentUserService 以统一获取当前用户 id。
 */
@RestController
@RequestMapping("/api/items")
@Validated
@Tag(name = "拍品管理", description = "拍品的增删改查、图片上传等接口")
public class ItemController {

    @Value("${image.max-size-bytes-controller:2097152}")
    private long maxSizeBytesController;

    private final ItemService itemService;
    private final StorageService storageService;
    @Getter
    private final UserService userService;
    private final CurrentUserService currentUserService;

    public ItemController(ItemService itemService, StorageService storageService, UserService userService, CurrentUserService currentUserService) {
        this.itemService = itemService;
        this.storageService = storageService;
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "分页查询拍品列表", description = "支持按标题、分类、状态筛选")
    @GetMapping
    public ResponseEntity<?> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "标题关键字") @RequestParam(required = false) String title,
            @Parameter(description = "分类") @RequestParam(required = false) String category,
            @Parameter(description = "状态") @RequestParam(required = false) String status
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

    @Operation(summary = "获取拍品详情", description = "根据拍品ID获取详细信息")
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@Parameter(description = "拍品ID") @PathVariable Long id) {
        Item item = itemService.getById(id);
        if (item == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("item not found");
        return ResponseEntity.ok(toDto(item));
    }

    @Operation(summary = "创建拍品", description = "创建新的拍品，需要登录")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateItemRequest req) {
        Optional<Long> optId = currentUserService.getCurrentUserId();
        if (optId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        Item created = itemService.create(req, optId.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @Operation(summary = "更新拍品", description = "更新拍品信息，仅创建者或管理员可操作")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@Parameter(description = "拍品ID") @PathVariable Long id, @Valid @RequestBody CreateItemRequest req) {
        Optional<Long> optId = currentUserService.getCurrentUserId();
        if (optId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        Item existing = itemService.getById(id);
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("item not found");
        // 仅创建者或 ADMIN 能更新
        if (!existing.getCreatedBy().equals(optId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限修改该拍品");
        }
        try {
            Item updated = itemService.update(id, req);
            return ResponseEntity.ok(toDto(updated));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @Operation(summary = "删除拍品", description = "删除拍品，仅创建者或管理员可操作")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@Parameter(description = "拍品ID") @PathVariable Long id) {
        Optional<Long> optId = currentUserService.getCurrentUserId();
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

    @Operation(summary = "上传拍品图片", description = "为拍品上传图片，仅创建者或管理员可操作")
    @PostMapping("/{id}/image")
    public ResponseEntity<?> uploadImage(@Parameter(description = "拍品ID") @PathVariable Long id, @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        if (file.getSize() > maxSizeBytesController) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", "文件大小超过限制: " + maxSizeBytesController + " bytes"));
        }

        Optional<Long> optId = currentUserService.getCurrentUserId();
        if (optId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");

        Item existing = itemService.getById(id);
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("item not found");
        // 仅创建者或 ADMIN 可以上传图片
        if (!existing.getCreatedBy().equals(optId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限为该拍品上传图片");
        }

        try {
            String folder = "items/" + id;
            String imageUrl = storageService.store(file, folder);
            Item item = itemService.updateImagePath(id, imageUrl);
            return ResponseEntity.ok().body(java.util.Map.of("imageUrl", imageUrl, "item", toDto(item)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of("error", ex.getMessage()));
        }
    }

    private ItemDTO toDto(Item item) {
        if (item == null) return null;
        ItemDTO dto = ItemDTO.builder().build();
        BeanUtils.copyProperties(item, dto);
        dto.setImageUrl(item.getImagePath());
        // 映射审核拒绝原因
        dto.setRejectReason(item.getRejectReason());
        return dto;
    }

}