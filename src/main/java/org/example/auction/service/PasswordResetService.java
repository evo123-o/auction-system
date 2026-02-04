package org.example.auction.service;

public interface PasswordResetService {
    /**
     * 发起重置：生成验证码并发送（或日志输出），返回掩码后的目标信息（如邮箱）。
     */
    void initiateByUsernameOrEmail(String usernameOrEmail);

    /**
     * 使用验证码重置密码。
     */
    void resetWithCode(String username, String code, String newPassword);
}