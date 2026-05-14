package com.qlda.userservice.Service;

import com.qlda.userservice.Entity.User;
import com.qlda.userservice.Exception.ResourceNotFoundException;
import com.qlda.userservice.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MyUserServiceDetail implements UserDetailsService {

    private final UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepo.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("user not found" + username));

        String authorities = user.getRole().name();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.getIsActive())
                .authorities(authorities)
                .build();
    }
}
