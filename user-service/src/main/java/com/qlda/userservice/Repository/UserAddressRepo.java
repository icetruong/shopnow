package com.qlda.userservice.Repository;

import com.qlda.userservice.Entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAddressRepo extends JpaRepository<UserAddress, UUID> {
    List<UserAddress> findAllByUser_Email(String email);
    Optional<UserAddress> findByIdAndUser_Email(UUID id,String email);

    long deleteByIdAndUser_Email(UUID id, String email);

}
