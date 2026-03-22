package com.ningcai.interview.service.auth;

import com.ningcai.interview.model.dto.RegisterRequest;
import com.ningcai.interview.model.entity.User;
import com.ningcai.interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    /**
     * 用户注册
     */
    @Transactional
    public User register(RegisterRequest request) {
        // 参数验证
        if (request == null) {
            throw new RuntimeException("注册信息不能为空");
        }
        
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查手机号是否已存在
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new RuntimeException("手机号已被注册");
            }
        }
        
        // 检查邮箱是否已存在
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("邮箱已被注册");
            }
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        
        // 保存到数据库
        return userRepository.save(user);
    }
    
    /**
     * 用户登录
     */
    @Transactional(readOnly = true)
    public User login(String account, String rawPassword) {
        // 参数验证
        if (account == null || account.trim().isEmpty()) {
            throw new RuntimeException("账号不能为空");
        }
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        
        // 根据账号查找用户
        User user = userRepository.findByAccount(account.trim())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 验证密码
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        
        return user;
    }
}