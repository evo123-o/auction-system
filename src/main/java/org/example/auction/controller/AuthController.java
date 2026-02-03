package org.example.auction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.LoginRequest;
import org.example.auction.entity.RefreshToken;
import org.example.auction.entity.User;
import org.example.auction.security.*;
import org.example.auction.service.PasswordResetService;
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
 * 已合并注册与密码重置接口（原 AuthExtraController 的功能）
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户登录、登出、注册、密码重置、Token 刷新等认证相关接口")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService; // 新增：密码重置服务

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenUtil jwtTokenUtil,
                          JwtProperties jwtProperties,
                          TokenBlacklistService tokenBlacklistService,
                          UserService userService,
                          RefreshTokenService refreshTokenService,
                          PasswordResetService passwordResetService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
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
            RefreshToken dbRt = refreshTokenService.rotateRefreshToken(refreshToken);
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

    // ===== 以下为原 AuthExtraController 的合并内容：注册、忘记密码、重置密码 =====

    /**
     * 用户注册接口
     */
    @Operation(summary = "用户注册", description = "注册新用户")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterReq req) {
        try {
            User u = userService.register(req.getUsername(), req.getPassword(), req.getEmail());
            return ResponseEntity.ok(ApiResponse.ok(u.getUsername())); // 返回成功响应，包含用户名
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage())); // 返回错误响应
        }
    }

    /**
     * 发起密码重置（发送验证码）
     */
    @Operation(summary = "发起密码重置（发送验证码）", description = "根据用户名或邮箱发送验证码，避免泄露是否存在账号")
    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgot(@Valid @RequestBody ForgotReq req) {
        passwordResetService.initiateByUsernameOrEmail(req.getUsernameOrEmail());
        // 始终返回成功消息以防止用户名枚举攻击
        return ResponseEntity.ok(ApiResponse.ok("如果账号存在，将向其发送验证码"));
    }

    /**
     * 使用验证码重置密码
     */
    @Operation(summary = "使用验证码重置密码", description = "使用验证码重置用户密码")
    @PostMapping("/password/reset")
    public ResponseEntity<?> reset(@Valid @RequestBody ResetReq req) {
        try {
            passwordResetService.resetWithCode(req.getUsername(), req.getCode(), req.getNewPassword());
            return ResponseEntity.ok(ApiResponse.ok("密码已重置")); // 返回成功响应
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage())); // 返回错误响应
        }
    }

    // DTOs for register/forgot/reset
    @Setter
    @Getter
    public static class RegisterReq {
        @NotBlank @Size(min = 3, max = 32) private String username; // 用户名必须在3到32个字符之间
        @NotBlank @Size(min = 6, max = 64) private String password; // 密码必须在6到64个字符之间
        @Email private String email; // 邮箱必须是有效的
    }

    @Setter
    @Getter
    public static class ForgotReq {
        @NotBlank private String usernameOrEmail; // 用户名或邮箱不能为空
    }

    @Setter
    @Getter
    public static class ResetReq {
        @NotBlank private String username; // 用户名不能为空
        @NotBlank @Size(min = 6, max = 6) private String code; // 验证码必须是6个字符
        @NotBlank @Size(min = 6, max = 64) private String newPassword; // 新密码必须在6到64个字符之间
    }

}

