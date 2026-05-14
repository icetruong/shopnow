package com.qlda.userservice.Controller;

import com.qlda.userservice.DTO.Request.User.ChangeRule;
import com.qlda.userservice.DTO.Response.Admin.UserAdminResponse;
import com.qlda.userservice.DTO.Response.Common.ApiResponse;
import com.qlda.userservice.DTO.Response.User.UserResponse;
import com.qlda.userservice.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserAdminResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam String sort,
            @RequestParam String direction,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean isActive
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "lấy thành công",
                        userService.getAllUserForAdmin(page, size, sort, direction, keyword, provider, isActive)
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "lấy thành công",
                        userService.getUserById(id)
                )
        );
    }

    @PatchMapping("/{id}/ban")
    public ResponseEntity<ApiResponse<UserResponse>> ban(@PathVariable UUID id)
    {
        userService.banUser(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "lấy thành công",
                        null
                )
        );
    }

    @PatchMapping("/{id}/unban")
    public ResponseEntity<ApiResponse<UserResponse>> unban(@PathVariable UUID id)
    {
        userService.unbanUser(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "lấy thành công",
                        null
                )
        );
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(@PathVariable UUID id, @RequestBody ChangeRule rule)
    {
        userService.updateRule(id, rule);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "lấy thành công",
                        null
                )
        );
    }


}
