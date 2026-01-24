package org.example.auction.controller;

import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.BreachRecord;
import org.example.auction.service.BreachService;
import org.example.auction.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 违约记录查询
 */
@RestController
@RequestMapping("/api/breaches")
public class BreachController {

    private final BreachService breachService;

    public BreachController(BreachService breachService) {
        this.breachService = breachService;
    }

    @GetMapping
    public ResponseEntity<?> myBreaches() {
        // 假设 SecurityUtils 能拿到当前用户 id
        Long userId = SecurityUtils.getCurrentUserId(null).orElse(null);
        if (userId == null) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
        List<BreachRecord> list = breachService.listUserBreaches(userId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
