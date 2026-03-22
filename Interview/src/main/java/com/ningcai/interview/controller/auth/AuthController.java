package com.ningcai.interview.controller.auth;

import com.ningcai.interview.model.dto.*;
import com.ningcai.interview.model.entity.User;
import com.ningcai.interview.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.register(request);
            UserResponse response = new UserResponse(user);
            return ResponseEntity.ok(ApiResponse.success("注册成功", response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("服务器内部错误：" + e.getMessage()));
        }
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = authService.login(request.getAccount(), request.getPassword());
            UserResponse response = new UserResponse(user);
            return ResponseEntity.ok(ApiResponse.success("登录成功", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("服务器内部错误：" + e.getMessage()));
        }
    }

    /**
     * 健康检查
     * GET /api/auth/health
     */
    @GetMapping("/health")
    public String health() {
        return "Auth service is running";
    }
}