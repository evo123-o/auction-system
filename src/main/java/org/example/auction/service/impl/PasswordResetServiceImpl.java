package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.auction.entity.PasswordResetToken;
import org.example.auction.entity.User;
import org.example.auction.mapper.PasswordResetMapper;
import org.example.auction.service.MailService;
import org.example.auction.service.PasswordResetService;
import org.example.auction.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final PasswordResetMapper resetMapper;
    private final UserService userService;
    private final MailService mailService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public PasswordResetServiceImpl(PasswordResetMapper resetMapper, UserService userService, MailService mailService) {
        this.resetMapper = resetMapper;
        this.userService = userService;
        this.mailService = mailService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initiateByUsernameOrEmail(String usernameOrEmail) {
        User user = userService.findByUsernameOrEmail(usernameOrEmail);
        if (user == null) {
            // 为防止枚举用户名，这里统一返回成功，但不创建记录
            return;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        PasswordResetToken pr = new PasswordResetToken();
        pr.setUserId(user.getId());
        pr.setCode(code);
        pr.setToken(null); // 如需要链接式重置可生成UUID
        pr.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        pr.setUsed(false);
        pr.setCreatedAt(LocalDateTime.now());
        resetMapper.insert(pr);

        // 发送验证码
        try {
            mailService.sendPasswordResetCode(user.getEmail(), code);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetWithCode(String username, String code, String newPassword) {
        User user = userService.findByUsername(username);
        if (user == null) throw new IllegalArgumentException("用户不存在");

        PasswordResetToken pr = resetMapper.selectOne(
                new LambdaQueryWrapper<PasswordResetToken>()
                        .eq(PasswordResetToken::getUserId, user.getId())
                        .eq(PasswordResetToken::getCode, code)
                        .eq(PasswordResetToken::getUsed, false)
                        .orderByDesc(PasswordResetToken::getCreatedAt)
                        .last("limit 1")
        );
        if (pr == null) throw new IllegalArgumentException("验证码错误");
        if (pr.getExpiresAt() != null && LocalDateTime.now().isAfter(pr.getExpiresAt())) {
            throw new IllegalArgumentException("验证码已过期");
        }

        user.setPassword(encoder.encode(newPassword));
        userService.update(user); // 需要在 UserService 提供 update 方法
        pr.setUsed(true);
        resetMapper.updateById(pr);
    }
}
