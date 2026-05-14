package com.qlda.userservice.DTO.Response.Admin;

import com.qlda.userservice.DTO.Response.User.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserAdminResponse {
    List<UserResponse> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
    boolean isLast;
}
