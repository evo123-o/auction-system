package org.example.auction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Null;
import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Item;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.ItemService;
import org.example.auction.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 额外的拍品管理接口（开拍）
 */
@RestController
@RequestMapping("/api/items")
@Tag(name = "拍品管理", description = "拍品的增删改查、图片上传等接口")
public class ItemAdminController {

    private final ItemService itemService;
    private final CurrentUserService currentUserService;

    public ItemAdminController(ItemService itemService, CurrentUserService currentUserService) {
        this.itemService = itemService;
        this.currentUserService = currentUserService;
    }

    /**
     * 将拍品状态置为 RUNNING（仅拍品创建者或管理员）
     */
    @Operation(summary = "开始拍卖", description = "将拍品状态置为进行中，仅创建者或管理员可操作")
    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(@Parameter(description = "拍品ID") @PathVariable Long id) {
        var optUser = currentUserService.getCurrentUserId();
        if (optUser.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        Long userId = optUser.get();

        Item item = itemService.getById(id);
        if (item == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("拍品不存在"));

        if (!item.getCreatedBy().equals(userId) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("无权开拍该拍品"));
        }

        Item updated = itemService.startAuction(id); // 需要在 ItemService 中实现：将 status=RUNNING、更新时间等
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

}
