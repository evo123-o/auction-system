package org.example.auction.controller;

import jakarta.validation.Valid;
import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.LoginRequest;
import org.example.auction.security.JwtProperties;
import org.example.auction.security.JwtTokenUtil;
import org.example.auction.security.TokenBlacklistService;
import org.example.auction.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

/**
 * AuthController：提供基于 JWT 的登录/登出示例
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenUtil jwtTokenUtil,
                          JwtProperties jwtProperties,
                          TokenBlacklistService tokenBlacklistService,
                          UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
            String token = jwtTokenUtil.generateToken(req.getUsername());
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "token", jwtProperties.getTokenPrefix() + token,
                    "expiresIn", jwtProperties.getExpirationSeconds()
            )));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(ApiResponse.fail("用户名或密码错误"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(name = "${jwt.header:Authorization}", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith(jwtProperties.getTokenPrefix())) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("缺少 token"));
        }
        String token = authHeader.substring(jwtProperties.getTokenPrefix().length());
        Date exp = jwtTokenUtil.getExpirationDateFromToken(token);
        long expiryMillis = exp != null ? exp.getTime() : (System.currentTimeMillis() + jwtProperties.getExpirationSeconds() * 1000);
        tokenBlacklistService.blacklist(token, expiryMillis);
        return ResponseEntity.ok(ApiResponse.ok("logout"));
    }
}