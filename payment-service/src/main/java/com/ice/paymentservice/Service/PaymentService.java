package com.ice.paymentservice.Service;

import com.ice.paymentservice.Client.MoMoClient;
import com.ice.paymentservice.Config.MoMoProperties;
import com.ice.paymentservice.Config.StripeProperties;
import com.ice.paymentservice.Config.VNPayProperties;
import com.ice.paymentservice.DTO.Request.Payment.CreatePaymentRequest;
import com.ice.paymentservice.DTO.Request.Payment.MoMoCreatePaymentRequest;
import com.ice.paymentservice.DTO.Request.Payment.MoMoIpnRequest;
import com.ice.paymentservice.DTO.Response.Payment.*;
import com.ice.paymentservice.Entity.Payment;
import com.ice.paymentservice.Entity.PaymentTransaction;
import com.ice.paymentservice.Entity.ProcessedWebhook;
import com.ice.paymentservice.Enum.*;
import com.ice.paymentservice.Exception.ResourceNotFoundException;
import com.ice.paymentservice.Repository.PaymentRepo;
import com.ice.paymentservice.Repository.PaymentTransactionRepo;
import com.ice.paymentservice.Repository.ProcessedWebhookRepo;
import com.ice.paymentservice.Specification.PaymentSpecification;
import com.ice.paymentservice.Util.MoMoUtil;
import com.ice.paymentservice.Util.VNPayUtil;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepo paymentRepo;
    private final PaymentTransactionRepo paymentTransactionRepo;
    private final ProcessedWebhookRepo processedWebhookRepo;
    private final VNPayProperties vnPayProperties;
    private final MoMoProperties moMoProperties;
    private final MoMoClient moMoClient;
    private final StripeProperties stripeProperties;

    @Transactional
    public Object createPayment(CreatePaymentRequest request, HttpServletRequest httpRequest) {

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

        String paymentUrl = buildPaymentUrl(saved, request, httpRequest);

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


    private String buildPaymentUrl(Payment payment, CreatePaymentRequest request, HttpServletRequest httpRequest) {
        return switch (payment.getMethod()) {
            case VNPAY -> buildVnPayUrl(payment, request, httpRequest);
            case MOMO -> buildMoMoUrl(payment, request);
            case STRIPE -> buildStripeUrl(payment, request);
            case COD -> throw new IllegalStateException("COD không đi vào nhánh này");
        };
    }

    private String buildVnPayUrl(Payment payment, CreatePaymentRequest request, HttpServletRequest httpRequest) {
        String returnUrl = (request.getReturnUrl() != null && !request.getReturnUrl().isBlank())
                ? request.getReturnUrl()
                : vnPayProperties.getReturnUrl();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", vnPayProperties.getVersion());
        params.put("vnp_Command", vnPayProperties.getCommand());
        params.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(payment.getAmount() * 100));
        params.put("vnp_CurrCode", vnPayProperties.getCurrCode());
        params.put("vnp_TxnRef", payment.getId().toString());
        params.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderCode());
        params.put("vnp_OrderType", vnPayProperties.getOrderType());
        params.put("vnp_Locale", vnPayProperties.getLocale());
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", VNPayUtil.getIpAddress(httpRequest));
        params.put("vnp_CreateDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("vnp_ExpireDate", payment.getExpiresAt().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        if (request.getBankCode() != null && !request.getBankCode().isBlank()) {
            params.put("vnp_BankCode", request.getBankCode());
        }

        return VNPayUtil.buildPaymentUrl(vnPayProperties.getPayUrl(), vnPayProperties.getHashSecret(), params);
    }

    /**
     * Chỉ để hiển thị kết quả cho user khi browser redirect về — KHÔNG dùng để cập nhật
     * PaymentStatus vì params ở return URL có thể bị giả mạo (không đáng tin bằng IPN).
     */
    public VNPayReturnResponse handleVnPayReturn(Map<String, String> params) {
        boolean validSignature = VNPayUtil.isValidSignature(params, vnPayProperties.getHashSecret());
        String responseCode = params.get("vnp_ResponseCode");

        return new VNPayReturnResponse(
                validSignature,
                params.get("vnp_TxnRef"),
                responseCode,
                validSignature && "00".equals(responseCode)
        );
    }

    @Transactional
    public VNPayIpnResponse handleVnPayIpn(Map<String, String> params) {
        if (!VNPayUtil.isValidSignature(params, vnPayProperties.getHashSecret())) {
            return VNPayIpnResponse.of("97", "Invalid signature");
        }

        String txnRef = params.get("vnp_TxnRef");
        Payment payment;
        try {
            payment = paymentRepo.findById(UUID.fromString(txnRef))
                    .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy payment"));
        } catch (Exception e) {
            return VNPayIpnResponse.of("01", "Order not found");
        }

        long vnpAmount = Long.parseLong(params.get("vnp_Amount")) / 100;
        if (vnpAmount != payment.getAmount()) {
            return VNPayIpnResponse.of("04", "Invalid amount");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return VNPayIpnResponse.of("02", "Order already confirmed");
        }

        String idempotencyKey = "VNPAY_IPN_" + txnRef;
        if (processedWebhookRepo.existsByIdempotencyKey(idempotencyKey)) {
            return VNPayIpnResponse.of("02", "Order already confirmed");
        }

        boolean isSuccess = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));

        payment.setStatus(isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        payment.setTransactionId(params.get("vnp_TransactionNo"));
        payment.setGatewayResponse(new HashMap<>(params));
        if (isSuccess) {
            payment.setPaidAt(LocalDateTime.now());
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.IPN)
                .gateway(Gateway.VNPAY)
                .gatewayTxnId(params.get("vnp_TransactionNo"))
                .amount(payment.getAmount())
                .status(isSuccess ? TransactionStatus.SUCCESS : TransactionStatus.FAILED)
                .rawPayload(new HashMap<>(params))
                .build();
        paymentTransactionRepo.save(transaction);

        ProcessedWebhook webhook = ProcessedWebhook.builder()
                .idempotencyKey(idempotencyKey)
                .gateway(Gateway.VNPAY)
                .paymentId(payment.getId())
                .build();
        processedWebhookRepo.save(webhook);

        return VNPayIpnResponse.of("00", "Confirm Success");
    }

    private String buildMoMoUrl(Payment payment, CreatePaymentRequest request) {
        String redirectUrl = (request.getReturnUrl() != null && !request.getReturnUrl().isBlank())
                ? request.getReturnUrl()
                : moMoProperties.getRedirectUrl();

        String requestId = UUID.randomUUID().toString();
        String orderId = payment.getId().toString();
        String orderInfo = "Thanh toan don hang " + payment.getOrderCode();
        String extraData = "";

        String rawSignature = MoMoUtil.buildCreateSignatureData(
                moMoProperties.getAccessKey(), payment.getAmount(), extraData,
                moMoProperties.getIpnUrl(), orderId, orderInfo,
                moMoProperties.getPartnerCode(), redirectUrl,
                requestId, moMoProperties.getRequestType()
        );
        String signature = MoMoUtil.hmacSHA256(moMoProperties.getSecretKey(), rawSignature);

        MoMoCreatePaymentRequest moMoRequest = new MoMoCreatePaymentRequest(
                moMoProperties.getPartnerCode(), requestId, payment.getAmount(), orderId, orderInfo,
                redirectUrl, moMoProperties.getIpnUrl(), moMoProperties.getRequestType(),
                extraData, moMoProperties.getLang(), signature
        );

        MoMoCreatePaymentResponse response = moMoClient.createPayment(moMoProperties.getEndpoint(), moMoRequest);

        if (response == null || response.getResultCode() != 0 || response.getPayUrl() == null) {
            throw new IllegalStateException("MoMo tạo giao dịch thất bại: "
                    + (response != null ? response.getMessage() : "không có phản hồi"));
        }

        return response.getPayUrl();
    }

    /**
     * MoMo không yêu cầu format response cố định như VNPay — chỉ cần HTTP 2xx là coi như đã
     * nhận, khác 2xx thì MoMo sẽ tự retry. Nên lỗi ở đây cứ throw để global handler trả mã lỗi.
     */
    @Transactional
    public void handleMoMoIpn(MoMoIpnRequest ipn) {
        String rawSignature = MoMoUtil.buildIpnSignatureData(
                moMoProperties.getAccessKey(), ipn.getAmount(), ipn.getExtraData(),
                ipn.getMessage(), ipn.getOrderId(), ipn.getOrderInfo(), ipn.getOrderType(),
                ipn.getPartnerCode(), ipn.getPayType(), ipn.getRequestId(),
                ipn.getResponseTime(), String.valueOf(ipn.getResultCode()), ipn.getTransId()
        );
        String computedSignature = MoMoUtil.hmacSHA256(moMoProperties.getSecretKey(), rawSignature);
        if (!computedSignature.equalsIgnoreCase(ipn.getSignature())) {
            throw new IllegalArgumentException("Invalid signature");
        }

        Payment payment = paymentRepo.findById(UUID.fromString(ipn.getOrderId()))
                .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy payment"));

        if (ipn.getAmount() != payment.getAmount()) {
            throw new IllegalArgumentException("Số tiền MoMo trả về không khớp payment");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        String idempotencyKey = "MOMO_IPN_" + ipn.getOrderId();
        if (processedWebhookRepo.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        boolean isSuccess = ipn.getResultCode() == 0;

        payment.setStatus(isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        payment.setTransactionId(ipn.getTransId());
        if (isSuccess) {
            payment.setPaidAt(LocalDateTime.now());
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.IPN)
                .gateway(Gateway.MOMO)
                .gatewayTxnId(ipn.getTransId())
                .amount(payment.getAmount())
                .status(isSuccess ? TransactionStatus.SUCCESS : TransactionStatus.FAILED)
                .rawPayload(Map.of(
                        "orderId", ipn.getOrderId(),
                        "resultCode", ipn.getResultCode(),
                        "message", ipn.getMessage()
                ))
                .build();
        paymentTransactionRepo.save(transaction);

        ProcessedWebhook webhook = ProcessedWebhook.builder()
                .idempotencyKey(idempotencyKey)
                .gateway(Gateway.MOMO)
                .paymentId(payment.getId())
                .build();
        processedWebhookRepo.save(webhook);
    }

    private String buildStripeUrl(Payment payment, CreatePaymentRequest request) {
        Stripe.apiKey = stripeProperties.getSecretKey();

        String successUrl = (request.getReturnUrl() != null && !request.getReturnUrl().isBlank())
                ? request.getReturnUrl()
                : stripeProperties.getSuccessUrl();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(stripeProperties.getCancelUrl())
                .putMetadata("paymentId", payment.getId().toString())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(stripeProperties.getCurrency())
                                                // VND là zero-decimal currency trên Stripe nên không nhân 100 như VNPay/cents
                                                .setUnitAmount(payment.getAmount())
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Thanh toan don hang " + payment.getOrderCode())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        try {
            Session session = Session.create(params);
            return session.getUrl();
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe tạo Checkout Session thất bại: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void handleStripeWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid signature");
        }

        if (!"checkout.session.completed".equals(event.getType())) {
            return;
        }

        String idempotencyKey = "STRIPE_EVENT_" + event.getId();
        if (processedWebhookRepo.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new IllegalStateException("Không đọc được dữ liệu event từ Stripe"));

        String paymentId = session.getMetadata().get("paymentId");
        Payment payment = paymentRepo.findById(UUID.fromString(paymentId))
                .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy payment"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        long paidAmount = session.getAmountTotal() != null ? session.getAmountTotal() : 0;
        if (paidAmount != payment.getAmount()) {
            throw new IllegalArgumentException("Số tiền Stripe trả về không khớp payment");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionId(session.getPaymentIntent());
        payment.setPaidAt(LocalDateTime.now());

        PaymentTransaction transaction = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.IPN)
                .gateway(Gateway.STRIPE)
                .gatewayTxnId(session.getPaymentIntent())
                .amount(payment.getAmount())
                .status(TransactionStatus.SUCCESS)
                .rawPayload(Map.of("eventId", event.getId(), "eventType", event.getType()))
                .build();
        paymentTransactionRepo.save(transaction);

        ProcessedWebhook webhook = ProcessedWebhook.builder()
                .idempotencyKey(idempotencyKey)
                .gateway(Gateway.STRIPE)
                .paymentId(payment.getId())
                .build();
        processedWebhookRepo.save(webhook);
    }
}
