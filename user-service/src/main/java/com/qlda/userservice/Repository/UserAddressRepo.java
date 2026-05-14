package com.qlda.userservice.Repository;

import com.qlda.userservice.Entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAddressRepo extends JpaRepository<UserAddress, UUID> {
}
