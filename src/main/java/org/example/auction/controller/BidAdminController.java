package org.example.auction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.PageResponse;
import org.example.auction.entity.Bid;
import org.example.auction.service.BidService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/bids")
@Tag(name = "管理员 - 竞拍管理", description = "管理员用的竞拍管理接口")
@PreAuthorize("hasRole('ADMIN')")
public class BidAdminController {

    private final BidService bidService;

    public BidAdminController(BidService bidService) {
        this.bidService = bidService;
    }

    @Operation(summary = "分页查询所有出价")
    @GetMapping
    public ResponseEntity<?> listBids(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 适配 PageRequest (0-based)
        int pageNo = page > 0 ? page - 1 : 0;
        Pageable p = PageRequest.of(pageNo, size);

        Page<@NonNull Bid> results = bidService.pageAll(p);

        PageResponse<Bid> resp = PageResponse.<Bid>builder()
                .total(results.getTotalElements())
                .pages(results.getTotalPages())
                .current(page)
                .size(size)
                .records(results.getContent())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @Operation(summary = "撤销出价")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBid(@PathVariable Long id) {
        try {
            bidService.cancelBid(id);
            return ResponseEntity.ok(ApiResponse.ok("已撤销"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ex.getMessage()));
        }
    }
}
