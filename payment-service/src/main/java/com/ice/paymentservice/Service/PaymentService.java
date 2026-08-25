package com.ice.paymentservice.Service;

import com.ice.paymentservice.DTO.Request.Payment.CreatePaymentRequest;
import com.ice.paymentservice.DTO.Response.Payment.*;
import com.ice.paymentservice.Entity.Payment;
import com.ice.paymentservice.Entity.PaymentTransaction;
import com.ice.paymentservice.Enum.*;
import com.ice.paymentservice.Exception.ResourceNotFoundException;
import com.ice.paymentservice.Repository.PaymentRepo;
import com.ice.paymentservice.Repository.PaymentTransactionRepo;
import com.ice.paymentservice.Specification.PaymentSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepo paymentRepo;
    private final PaymentTransactionRepo paymentTransactionRepo;

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

    public PaymentInternalResponse getPaymentInternal(String orderId) {
        Payment payment = paymentRepo.findByOrderId(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy payment"));

        return new PaymentInternalResponse(
                payment.getId().toString(),
                payment.getOrderId().toString(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt() != null
                        ? payment.getPaidAt().atZone(ZoneId.systemDefault()).toInstant()
                        : null
        );
    }

    public ConfirmCodResponse confirmCodPayment(String paymentId) {
        Payment payment = paymentRepo.findById(UUID.fromString(paymentId))
                .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy payment"));

        if(payment.getMethod() != PaymentMethod.COD || payment.getStatus() != PaymentStatus.PENDING)
        {
            throw new IllegalArgumentException("Không thể confirm ở trạng thái này");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());

        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.CHARGE)
                .gateway(Gateway.COD)
                .amount(payment.getAmount())
                .status(TransactionStatus.SUCCESS)
                .rawPayload(Map.of(
                        "source", "internal-confirm-cod",
                        "note", "COD xác nhận thủ công khi Order Service báo đã giao hàng, không qua cổng thanh toán"
                ))
                .build();

        paymentTransactionRepo.save(paymentTransaction);

        return new ConfirmCodResponse(
                payment.getId().toString(),
                payment.getStatus().toString(),
                payment.getPaidAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }

    public PaymentPageResponse getPaymentDetail(int page, int size, PaymentStatus status, PaymentMethod method, LocalDate startDate, LocalDate endDate) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        Specification<Payment> specification = Specification
                .where(PaymentSpecification.hasMethod(method))
                .and(PaymentSpecification.hasStatus(status))
                .and(PaymentSpecification.betweenDays(start, end));

        Page<Payment> payments = paymentRepo.findAll(specification, PageRequest.of(page, size));

        List<PaymentDetailResponse> paymentDetailResponseList = payments.stream()
                .map(payment -> new PaymentDetailResponse(
                        payment.getId().toString(),
                        payment.getOrderId().toString(),
                        payment.getOrderCode(),
                        payment.getUserId().toString(),
                        payment.getMethod(),
                        payment.getAmount(),
                        payment.getStatus(),
                        payment.getTransactionId(),
                        payment.getPaidAt() != null
                                ? payment.getPaidAt().atZone(ZoneId.systemDefault()).toInstant()
                                : null,
                        payment.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                )).toList();

        return new PaymentPageResponse(
                paymentDetailResponseList,
                payments.getNumber(),
                payments.getSize(),
                payments.getTotalElements(),
                payments.getTotalPages()
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
