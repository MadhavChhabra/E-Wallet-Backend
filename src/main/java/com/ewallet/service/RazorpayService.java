package com.ewallet.service;

import com.ewallet.dto.request.CreateOrderRequest;
import com.ewallet.dto.request.VerifyPaymentRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.dto.response.PaymentVerificationResponse;
import com.ewallet.dto.response.RazorpayOrderResponse;
import com.ewallet.model.PaymentOrder;
import com.ewallet.model.PaymentStatus;
import com.ewallet.repository.PaymentOrderRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.security.CurrentUserService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Server-side Razorpay integration for wallet top-ups (TEST mode).
 *
 * <p>Flow: {@code createOrder} → client opens Razorpay Checkout → client posts
 * the signed result to {@code verifyPayment}. The signature is verified with the
 * secret key server-side (never trusting the client), and only then is the
 * wallet credited. Crediting is idempotent on the Razorpay payment id.
 */
@Slf4j(topic = "RazorpayService")
@Service
public class RazorpayService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final BankAccountService bankAccountService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final Clock clock;

    @Value("${app.razorpay.enabled:false}")
    private boolean enabled;

    @Value("${app.razorpay.key-id:}")
    private String keyId;

    @Value("${app.razorpay.key-secret:}")
    private String keySecret;

    @Value("${app.razorpay.currency:INR}")
    private String currency;

    @Value("${app.razorpay.webhook-secret:}")
    private String webhookSecret;

    private volatile RazorpayClient client;

    public RazorpayService(PaymentOrderRepository paymentOrderRepository,
                           BankAccountService bankAccountService,
                           CurrentUserService currentUserService,
                           UserRepository userRepository,
                           Clock clock) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.bankAccountService = bankAccountService;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public RazorpayOrderResponse createOrder(CreateOrderRequest request) {
        ensureConfigured();

        // Enforces that the target wallet belongs to the authenticated user
        // (findByIban throws AccessDenied otherwise).
        bankAccountService.findByIban(request.getToBankAccountIban());

        long amountInPaise = toMinorUnits(request.getAmount());
        String receipt = "topup_" + currentUserService.getCurrentUserId() + "_" + System.currentTimeMillis();

        String orderId;
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);
            orderRequest.put("payment_capture", true);
            orderId = razorpay().orders.create(orderRequest).get("id");
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create payment order");
        }

        PaymentOrder order = new PaymentOrder();
        order.setRazorpayOrderId(orderId);
        order.setAmount(request.getAmount());
        order.setCurrency(currency);
        order.setTargetIban(request.getToBankAccountIban());
        order.setStatus(PaymentStatus.CREATED);
        order.setUser(userRepository.getReferenceById(currentUserService.getCurrentUserId()));
        order.setCreatedAt(LocalDateTime.now(clock));
        paymentOrderRepository.save(order);

        log.info("AUDIT razorpay-order-created orderId={} amount={} iban={} userId={}",
                orderId, request.getAmount(), request.getToBankAccountIban(), currentUserService.getCurrentUserId());

        return RazorpayOrderResponse.builder()
                .orderId(orderId)
                .keyId(keyId)
                .amountInPaise(amountInPaise)
                .amount(request.getAmount())
                .currency(currency)
                .receipt(receipt)
                .build();
    }

    @Transactional
    public PaymentVerificationResponse verifyPayment(VerifyPaymentRequest request) {
        ensureConfigured();

        PaymentOrder order = paymentOrderRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));

        if (!order.getUser().getId().equals(currentUserService.getCurrentUserId())) {
            throw new AccessDeniedException("You do not have access to this payment order");
        }

        // Already processed — return the recorded result (idempotent).
        if (order.getStatus() == PaymentStatus.PAID) {
            return toResponse(order);
        }

        boolean valid = verifySignature(request);
        if (!valid) {
            order.setStatus(PaymentStatus.FAILED);
            order.setUpdatedAt(LocalDateTime.now(clock));
            paymentOrderRepository.save(order);
            log.warn("AUDIT razorpay-verify-failed orderId={} userId={}",
                    request.getRazorpayOrderId(), currentUserService.getCurrentUserId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature verification failed");
        }

        // Credit the wallet, idempotent on the payment id.
        CommandResponse tx = bankAccountService.topUp(
                order.getTargetIban(), order.getAmount(), request.getRazorpayPaymentId());

        order.setStatus(PaymentStatus.PAID);
        order.setRazorpayPaymentId(request.getRazorpayPaymentId());
        order.setWalletTransactionId(tx.getId());
        order.setUpdatedAt(LocalDateTime.now(clock));
        paymentOrderRepository.save(order);

        log.info("AUDIT razorpay-verify-success orderId={} paymentId={} txId={} amount={} userId={}",
                order.getRazorpayOrderId(), request.getRazorpayPaymentId(), tx.getId(),
                order.getAmount(), currentUserService.getCurrentUserId());

        return toResponse(order);
    }

    /**
     * Authoritative, idempotent crediting path driven by Razorpay webhooks
     * (e.g. {@code payment.captured}). This is the source of truth: even if the
     * client never calls {@code /verify} (app closed, network dropped), a
     * captured payment still credits the wallet exactly once. The raw request
     * body is HMAC-verified against the configured webhook secret.
     */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("Received Razorpay webhook but no webhook secret is configured; ignoring");
            return;
        }

        try {
            if (!Utils.verifyWebhookSignature(payload, signature, webhookSecret)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
            }
        } catch (RazorpayException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.optString("event");
        if (!"payment.captured".equals(eventType) && !"order.paid".equals(eventType)) {
            log.info("Ignoring Razorpay webhook event {}", eventType);
            return;
        }

        JSONObject entity = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        String orderId = entity.optString("order_id");
        String paymentId = entity.optString("id");
        if (!StringUtils.hasText(orderId)) {
            log.warn("Razorpay webhook {} without order_id; ignoring", eventType);
            return;
        }

        PaymentOrder order = paymentOrderRepository.findByRazorpayOrderId(orderId).orElse(null);
        if (order == null) {
            log.warn("Razorpay webhook for unknown order {}; ignoring", orderId);
            return;
        }
        if (order.getStatus() == PaymentStatus.PAID) {
            return; // Already credited (idempotent).
        }

        CommandResponse tx = bankAccountService.systemTopUp(
                order.getTargetIban(), order.getAmount(), paymentId, order.getUser().getId());

        order.setStatus(PaymentStatus.PAID);
        order.setRazorpayPaymentId(paymentId);
        order.setWalletTransactionId(tx.getId());
        order.setUpdatedAt(LocalDateTime.now(clock));
        paymentOrderRepository.save(order);

        log.info("AUDIT razorpay-webhook-credited orderId={} paymentId={} txId={} amount={} userId={}",
                orderId, paymentId, tx.getId(), order.getAmount(), order.getUser().getId());
    }

    private boolean verifySignature(VerifyPaymentRequest request) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.getRazorpayOrderId());
            attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
            attributes.put("razorpay_signature", request.getRazorpaySignature());
            return Utils.verifyPaymentSignature(attributes, keySecret);
        } catch (RazorpayException e) {
            log.warn("Razorpay signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private PaymentVerificationResponse toResponse(PaymentOrder order) {
        return PaymentVerificationResponse.builder()
                .status(order.getStatus().name())
                .razorpayOrderId(order.getRazorpayOrderId())
                .razorpayPaymentId(order.getRazorpayPaymentId())
                .walletTransactionId(order.getWalletTransactionId())
                .amountCredited(order.getAmount())
                .build();
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private RazorpayClient razorpay() {
        RazorpayClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    try {
                        local = new RazorpayClient(keyId, keySecret);
                        client = local;
                    } catch (RazorpayException e) {
                        throw new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway is unavailable");
                    }
                }
            }
        }
        return local;
    }

    private void ensureConfigured() {
        if (!enabled || !StringUtils.hasText(keySecret) || !StringUtils.hasText(keyId)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay payments are not configured. Set RAZORPAY_ENABLED=true and provide test keys.");
        }
    }
}
