package org.example.auction.controller;

import jakarta.validation.Valid;
import org.example.auction.dto.*;
import org.example.auction.entity.User;
import org.example.auction.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * 改进版认证控制器：
 * - POST /api/auth/register  注册（带重复用户名校验）
 * - POST /api/auth/login     登录（示例 - 使用 AuthenticationManager 验证）
 * - GET  /api/auth/ping      健康检查
 *
 * 说明：当前示例使用 Basic/JWT 可按需要替换，登录示例只是做验证演示（未发放 JWT）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @GetMapping("/login")
    public ResponseEntity<ApiResponse<String>> loginInfo() {
        return ResponseEntity.ok(ApiResponse.ok("请使用 POST /api/auth/login 提交 JSON 格式的 {username,password} 进行登录"));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDTO>> register(@Valid @RequestBody RegisterRequest req) {
        // 重复用户名检查
        if (userService.findByUsername(req.getUsername()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail("用户名已存在"));
        }
        User created = userService.register(req);
        UserDTO dto = UserDTO.builder()
                .id(created.getId())
                .username(created.getUsername())
                .email(created.getEmail())
                .role(created.getRole())
                .creditScore(created.getCreditScore())
                .status(created.getStatus())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserDTO>> login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
            );
            // 认证成功：在此处可以生成 JWT 并返回给客户端。当前示例只返回基本用户信息
            User u = userService.findByUsername(req.getUsername());
            UserDTO dto = UserDTO.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .email(u.getEmail())
                    .role(u.getRole())
                    .creditScore(u.getCreditScore())
                    .status(u.getStatus())
                    .build();
            return ResponseEntity.ok(ApiResponse.ok(dto));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("用户名或密码错误"));
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("账户被锁定"));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("认证失败"));
        }
    }
}