package com.ningcai.interview.model.dto;

import com.ningcai.interview.model.entity.User;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UserResponse {
    private Long id;
    private String username;
    private String phone;
    private String email;
    private String createdAt;
    private String updatedAt;
    
    public UserResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.phone = user.getPhone();
        this.email = user.getEmail();
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        if (user.getCreatedAt() != null) {
            this.createdAt = user.getCreatedAt().format(formatter);
        }
        if (user.getUpdatedAt() != null) {
            this.updatedAt = user.getUpdatedAt().format(formatter);
        }
    }
    
    // Getters
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}