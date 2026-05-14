package com.qlda.userservice.Service;

import com.qlda.userservice.DTO.Request.Auth.RegisterRequest;
import com.qlda.userservice.DTO.Response.Auth.RegisterResponse;
import com.qlda.userservice.Entity.User;
import com.qlda.userservice.Exception.EmailExistException;
import com.qlda.userservice.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public RegisterResponse register(RegisterRequest request)
    {
        if(userRepo.existsByEmail(request.getEmail()))
            throw new EmailExistException("Email already exists");

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .build();

        User save = userRepo.save(user);

        return new RegisterResponse(save.getId(), save.getEmail());
    }
}
