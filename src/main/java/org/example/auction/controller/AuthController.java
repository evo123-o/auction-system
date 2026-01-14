package org.example.auction.controller;

import org.example.auction.entity.User;
import org.example.auction.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public String register(@RequestBody User req) {
        // 简化校验，生产需更完整的验证
        if (req.getUsername() == null || req.getPassword() == null) {
            return "参数缺失";
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setEmail(req.getEmail());
        u.setRole("USER");
        u.setCreditScore(100);
        userMapper.insert(u);
        return "registered";
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}