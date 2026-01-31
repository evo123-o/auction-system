package org.example.auction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.LoginRequest;
import org.example.auction.entity.RefreshToken;
import org.example.auction.entity.User;
import org.example.auction.security.*;
import org.example.auction.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

/**
 * AuthController：JWT + Refresh Token 示例
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户登录、登出、Token 刷新等认证相关接口")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenUtil jwtTokenUtil,
                          JwtProperties jwtProperties,
                          TokenBlacklistService tokenBlacklistService,
                          UserService userService,
                          RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    @Operation(summary = "用户登录", description = "使用用户名和密码进行登录，返回 accessToken 和 refreshToken")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "用户名或密码错误")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
            String accessToken = jwtTokenUtil.generateToken(req.getUsername());
            User u = userService.findByUsername(req.getUsername());
            Long userId = u != null ? u.getId() : null;
            RefreshToken rt = refreshTokenService.createRefreshToken(userId);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "accessToken", jwtProperties.getTokenPrefix() + accessToken,
                    "expiresIn", jwtProperties.getExpirationSeconds(),
                    "refreshToken", rt.getToken()
            )));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(ApiResponse.fail("用户名或密码错误"));
        }
    }

    @Operation(summary = "用户登出", description = "使当前 accessToken 失效，可选提供 refreshToken 同时撤销")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登出成功")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(name = "${jwt.header:Authorization}", required = false) String authHeader,
                                    @Parameter(description = "刷新令牌") @RequestParam(name = "refreshToken", required = false) String refreshToken) {
        // 撤销 refresh token（如果提供）
        if (refreshToken != null) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }

        if (authHeader == null || !authHeader.startsWith(jwtProperties.getTokenPrefix())) {
            return ResponseEntity.ok(ApiResponse.ok("logged out"));
        }
        String token = authHeader.substring(jwtProperties.getTokenPrefix().length());
        Date exp = jwtTokenUtil.getExpirationDateFromToken(token);
        long expiryMillis = exp != null ? exp.getTime() : (System.currentTimeMillis() + jwtProperties.getExpirationSeconds() * 1000);
        tokenBlacklistService.blacklist(token, expiryMillis);
        return ResponseEntity.ok(ApiResponse.ok("logged out"));
    }

    @Operation(summary = "刷新令牌", description = "使用 refreshToken 获取新的 accessToken 和 refreshToken")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "刷新成功")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "refreshToken 无效或用户不存在")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Parameter(description = "刷新令牌") @RequestParam("refreshToken") String refreshToken) {
        try {
            RefreshToken newRt = refreshTokenService.rotateRefreshToken(refreshToken);
            RefreshToken dbRt = newRt;
            // 生成新的 access token（需要用户名）
            // 通过 dbRt.userId 找 username（用 userService）
            Long userId = dbRt.getUserId();
            String username = null;
            if (userId != null) {
                User u = userService.findById(userId);
                if (u != null) username = u.getUsername();
            }
            if (username == null) {
                return ResponseEntity.status(400).body(ApiResponse.fail("用户不存在"));
            }
            String accessToken = jwtTokenUtil.generateToken(username);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "accessToken", jwtProperties.getTokenPrefix() + accessToken,
                    "expiresIn", jwtProperties.getExpirationSeconds(),
                    "refreshToken", dbRt.getToken()
            )));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(ApiResponse.fail(ex.getMessage()));
        }
    }
}