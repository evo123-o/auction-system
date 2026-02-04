package org.example.auction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.AdminUserDto;
import org.example.auction.dto.CreateUserRequest;
import org.example.auction.dto.UpdateUserRequest;
import org.example.auction.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户管理接口：分页查询、查看、创建、更新、删除用户
 */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "管理员 - 用户管理", description = "管理员用的用户管理接口")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "分页查询用户")
    @GetMapping
    public ResponseEntity<?> listUsers(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        int page0 = page > 0 ? page - 1 : 0;
        Page<@NonNull AdminUserDto> users = adminUserService.listUsers(page0, size);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        AdminUserDto dto = adminUserService.getUser(id);
        if (dto == null) return ResponseEntity.status(404).body(ApiResponse.fail("用户未找到"));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest req) {
        try {
            AdminUserDto created = adminUserService.createUser(req);
            return ResponseEntity.status(201).body(ApiResponse.ok(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
        }
    }

    @Operation(summary = "更新用户（角色/启用状态等）")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        try {
            AdminUserDto updated = adminUserService.updateUser(id, req);
            if (updated == null) return ResponseEntity.status(404).body(ApiResponse.fail("用户未找到"));
            return ResponseEntity.ok(ApiResponse.ok(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
        }
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        boolean removed = adminUserService.deleteUser(id);
        if (!removed) return ResponseEntity.status(404).body(ApiResponse.fail("用户未找到"));
        return ResponseEntity.ok(ApiResponse.ok("deleted"));
    }
}
