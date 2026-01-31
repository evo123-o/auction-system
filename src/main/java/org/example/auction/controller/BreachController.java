package org.example.auction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.BreachRecord;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.BreachService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 违约记录查询（当前用户）
 */
@RestController
@RequestMapping("/api/breaches")
@Tag(name = "违约记录", description = "用户违约记录查询接口")
public class BreachController {

    private final BreachService breachService;
    private final CurrentUserService currentUserService;

    public BreachController(BreachService breachService, CurrentUserService currentUserService) {
        this.breachService = breachService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "我的违约记录", description = "获取当前用户的所有违约记录")
    @GetMapping
    public ResponseEntity<?> myBreaches() {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
        Long userId = optUserId.get();
        List<BreachRecord> list = breachService.listUserBreaches(userId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
