package org.example.auction.controller;

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

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(name = "${jwt.header:Authorization}", required = false) String authHeader,
                                    @RequestParam(name = "refreshToken", required = false) String refreshToken) {
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

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestParam("refreshToken") String refreshToken) {
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