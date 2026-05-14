package com.qlda.userservice.Controller;

import com.qlda.userservice.DTO.Request.User.ChangePasswordRequest;
import com.qlda.userservice.DTO.Request.User.UserRequest;
import com.qlda.userservice.DTO.Response.Common.ApiResponse;
import com.qlda.userservice.DTO.Response.User.AvatarResponse;
import com.qlda.userservice.DTO.Response.User.UserResponse;
import com.qlda.userservice.Service.FileStorageService;
import com.qlda.userservice.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(Authentication authentication)
    {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin thành công",
                userService.getUser(authentication.getName())
        ));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@Valid @RequestBody UserRequest userRequest, Authentication authentication)
    {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhập thông tin thành công",
                userService.updateUser(userRequest, authentication.getName())
        ));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarResponse>> updateAvatar(@RequestParam("file") MultipartFile file, Authentication authentication)
    {
        String avatarUrl = fileStorageService.saveAvatar(file);
        userService.saveAvatarUrl(avatarUrl, authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(
                "Ảnh thành công",
                new AvatarResponse(avatarUrl)
        ));
    }

    @PutMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        userService.changePassword(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", null));
    }

}
