package com.ice.paymentservice.Service;

import com.ice.paymentservice.DTO.Request.Payment.CreatePaymentRequest;
import com.ice.paymentservice.DTO.Response.Payment.CreatePaymentCODResponse;
import com.ice.paymentservice.DTO.Response.Payment.CreatePaymentOnlineResponse;
import com.ice.paymentservice.DTO.Response.Payment.PaymentResponse;
import com.ice.paymentservice.Entity.Payment;
import com.ice.paymentservice.Enum.PaymentMethod;
import com.ice.paymentservice.Enum.PaymentStatus;
import com.ice.paymentservice.Exception.ResourceNotFoundException;
import com.ice.paymentservice.Repository.PaymentRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepo paymentRepo;

    @Transactional
    public Object createPayment(CreatePaymentRequest request) {

        if(paymentRepo.existsByOrderId(UUID.fromString(request.getOrderId())))
        {
            throw new IllegalArgumentException("Đơn hàng đã có payment");
        }

        Payment payment = Payment.builder()
                .orderId(UUID.fromString(request.getOrderId()))
                .orderCode(request.getOrderCode())
                .userId(UUID.fromString(request.getUserId()))
                .method(request.getMethod())
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .build();

        if(request.getMethod() == PaymentMethod.COD)
        {
            Payment saved = paymentRepo.save(payment);
            return new CreatePaymentCODResponse(
                    saved.getId().toString(),
                    saved.getMethod(),
                    saved.getStatus(),
                    "Thanh toán khi nhận hàng."
            );
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        payment.setExpiresAt(expiresAt);
        Payment saved = paymentRepo.save(payment);

        String paymentUrl = buildPaymentUrl(saved, request);

        return new CreatePaymentOnlineResponse(
                saved.getId().toString(),
                saved.getOrderId().toString(),
                saved.getMethod(),
                saved.getAmount(),
                saved.getStatus(),
                paymentUrl,
                saved.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }

    public PaymentResponse getPayment(String paymentId, String userId) {
        Payment payment = paymentRepo.findByIdAndUserId(UUID.fromString(paymentId), UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy payment"));

        return new PaymentResponse(
                payment.getId().toString(),
                payment.getOrderId().toString(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getTransactionId(),
                payment.getPaidAt() != null
                        ? payment.getPaidAt().atZone(ZoneId.systemDefault()).toInstant()
                        : null,
                payment.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }

    private String buildPaymentUrl(Payment payment, CreatePaymentRequest request) {
        return switch (payment.getMethod()) {
            case VNPAY -> "TODO: build VNPay URL (HMAC-SHA512) — chưa có config secret/terminal ID";
            case MOMO -> "TODO: gọi MoMo API tạo transaction, lấy payUrl";
            case STRIPE -> "TODO: tạo Stripe Checkout Session";
            case COD -> throw new IllegalStateException("COD không đi vào nhánh này");
        };
    }


}
