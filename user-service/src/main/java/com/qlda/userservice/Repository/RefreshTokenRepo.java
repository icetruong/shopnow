package com.qlda.userservice.Repository;

import com.qlda.userservice.Entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepo extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenAndUser_Email(String token, String email);
}
