package com.qlda.userservice.DTO.Response.User;

import com.qlda.userservice.Enum.UserProvider;
import com.qlda.userservice.Enum.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String userId;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private UserRole role;
    private UserProvider provider;
    private boolean emailVerified;
    private boolean isActive;
    private LocalDateTime createdAt;
}
