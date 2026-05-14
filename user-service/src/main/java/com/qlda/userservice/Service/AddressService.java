package com.qlda.userservice.Service;

import com.qlda.userservice.DTO.Request.Address.AddressRequest;
import com.qlda.userservice.DTO.Response.Address.AddressResponse;
import com.qlda.userservice.Entity.User;
import com.qlda.userservice.Entity.UserAddress;
import com.qlda.userservice.Exception.AppException;
import com.qlda.userservice.Exception.ResourceNotFoundException;
import com.qlda.userservice.Repository.UserAddressRepo;
import com.qlda.userservice.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {
    private final UserAddressRepo userAddressRepo;
    private final UserRepo userRepo;

    public List<AddressResponse> getAllAddress(String email)
    {
        List<UserAddress> addresses = userAddressRepo.findAllByUser_Email(email);

        return addresses.stream().map(this::toAddressResponse).toList();
    }

    public AddressResponse addAddress(AddressRequest request, String email)
    {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getPhone() == null)
            throw new AppException("Vui lòng cập nhật số điện thoại trước khi thêm địa chỉ");

        if(request.isDefault())
            setAllNotDefault(email);

        UserAddress save = userAddressRepo.save(UserAddress.builder()
                .user(user)
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .province(request.getProvince())
                .district(request.getDistrict())
                .ward(request.getWard())
                .streetDetail(request.getStreetDetail())
                .isDefault(request.isDefault())
                .build()
        );

        return toAddressResponse(save);
    }

    public AddressResponse changeAddress(UUID id, AddressRequest request, String email)
    {
        UserAddress userAddress = userAddressRepo.findByIdAndUser_Email(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if(request.isDefault())
            setAllNotDefault(email);
        userAddress.setProvince(request.getProvince());
        userAddress.setDistrict(request.getDistrict());
        userAddress.setWard(request.getWard());
        userAddress.setIsDefault(request.isDefault());
        userAddress.setStreetDetail(request.getStreetDetail());

        UserAddress save = userAddressRepo.save(userAddress);

        return toAddressResponse(save);
    }

    @Transactional
    public void deleteAddress(UUID id, String email)
    {
        if(userAddressRepo.deleteByIdAndUser_Email(id, email) == 0)
            throw new IllegalArgumentException("địa chỉ và người dùng không đúng");

    }

    public void setAddressDefault(UUID id, String email)
    {
        setAllNotDefault(email);
        UserAddress userAddress = userAddressRepo.findByIdAndUser_Email(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        userAddress.setIsDefault(true);
        userAddressRepo.save(userAddress);
    }

    private void setAllNotDefault(String email)
    {
        List<UserAddress> addresses = userAddressRepo.findAllByUser_Email(email);
        for(UserAddress userAddress : addresses)
        {
            userAddress.setIsDefault(false);
        }
        userAddressRepo.saveAll(addresses);
    }

    private AddressResponse toAddressResponse(UserAddress userAddress)
    {
        return new AddressResponse(
                userAddress.getId().toString(),
                userAddress.getFullName(),
                userAddress.getPhone(),
                userAddress.getProvince(),
                userAddress.getDistrict(),
                userAddress.getWard(),
                userAddress.getStreetDetail(),
                userAddress.getIsDefault()
        );
    }
}
