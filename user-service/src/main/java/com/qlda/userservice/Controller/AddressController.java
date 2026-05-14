package com.qlda.userservice.Controller;

import com.qlda.userservice.DTO.Request.Address.AddressRequest;
import com.qlda.userservice.DTO.Response.Address.AddressResponse;
import com.qlda.userservice.DTO.Response.Common.ApiResponse;
import com.qlda.userservice.Service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/addresses")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAll(Authentication authentication)
    {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách địa chỉ thành công",
                addressService.getAllAddress(authentication.getName())
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(@Valid @RequestBody AddressRequest request, Authentication authentication)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Thêm thành công",
                        addressService.addAddress( request, authentication.getName())
                ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(@PathVariable UUID id, @Valid @RequestBody AddressRequest request, Authentication authentication)
    {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhập thành công",
                addressService.changeAddress(id, request,authentication.getName())
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID id, Authentication authentication)
    {
        addressService.deleteAddress(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Xóa thành công",
                null
        ));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<Void>> patchAddress(@PathVariable UUID id, Authentication authentication)
    {
        addressService.setAddressDefault(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhập thành công",
                null
        ));
    }
}
