package org.example.auction.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.example.auction.dto.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 全局异常处理：把常见异常返回为统一的 JSON 响应，避免 Whitelabel 页面。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseBody
    public ResponseEntity<@NonNull ApiResponse<String>> handleMethodNotSupported(HttpServletRequest req,
                                                                                 HttpRequestMethodNotSupportedException ex) {
        String msg = "不支持的请求方法: " + ex.getMethod() + ". 请使用 " + String.join(", ", ex.getSupportedMethods()) + ".";
        ApiResponse<String> body = ApiResponse.fail(msg);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<@NonNull ApiResponse<String>> handleGenericException(HttpServletRequest req, Exception ex) {
        // 对开发阶段可以返回消息；生产环境避免泄露内部细节
        ApiResponse<String> body = ApiResponse.fail("服务器内部错误: " + ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(headers).body(body);
    }
}