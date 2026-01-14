package org.example.auction.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 简易根页面与错误信息接口，避免 Whitelabel 干扰。
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "Auction backend is running. Try GET /api/auth/ping";
    }

    @RequestMapping("/error-info")
    public Map<String, Object> handleError(HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object messageObj = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Integer status = (statusObj instanceof Integer) ? (Integer) statusObj : null;
        String message = messageObj != null ? messageObj.toString() : null;

        Map<String, Object> body = new HashMap<>();
        body.put("status", status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("message", message != null ? message : "Unexpected error");
        // 可根据需要加入更多信息（timestamp/path 等），生产环境注意不要泄露敏感信息
        body.put("path", request.getRequestURI());
        return body;
    }
}
