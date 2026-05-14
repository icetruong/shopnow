package com.qlda.userservice.Controller.Internal;

import com.qlda.userservice.DTO.Response.User.InternalExistUserResponse;
import com.qlda.userservice.DTO.Response.User.InternalUserResponse;
import com.qlda.userservice.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/users/{id}")
    public ResponseEntity<InternalUserResponse> getUser(@PathVariable UUID id)
    {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @GetMapping("/users/{id}/exists")
    public ResponseEntity<InternalExistUserResponse> checkUser(@PathVariable UUID id)
    {
        return ResponseEntity.ok(userService.checkUser(id));
    }

}
