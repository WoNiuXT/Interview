package com.ningcai.interview.model.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    
    @NotBlank(message = "账号不能为空")
    private String account;  // 用户名/手机号/邮箱
    
    @NotBlank(message = "密码不能为空")
    private String password;
    
    // Getters and Setters
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}