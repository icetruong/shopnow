package com.ice.shippingservice.Exception;

/**
 * Không map được địa chỉ text sang mã nhà vận chuyển.
 * Lưu ý: shipment VẪN được INSERT dạng PENDING trước khi ném exception này
 * (ném ở controller, sau khi transaction của service đã commit) -> HTTP 422.
 */
public class AddressMappingFailedException extends RuntimeException {
    public AddressMappingFailedException(String message) {
        super(message);
    }
}
