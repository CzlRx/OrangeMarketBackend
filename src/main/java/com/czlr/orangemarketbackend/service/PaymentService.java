package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import com.czlr.orangemarketbackend.common.enums.PaymentStatus;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AlipayProperties;
import com.czlr.orangemarketbackend.entity.dto.CancelOrderRequest;
import com.czlr.orangemarketbackend.entity.dto.PayOrderRequest;
import com.czlr.orangemarketbackend.entity.dto.PayOrderResultDTO;
import com.czlr.orangemarketbackend.entity.po.Order;
import com.czlr.orangemarketbackend.entity.po.OrderItem;
import com.czlr.orangemarketbackend.entity.po.PaymentTransaction;
import com.czlr.orangemarketbackend.mapper.OrderItemMapper;
import com.czlr.orangemarketbackend.mapper.PaymentTransactionMapper;
import com.czlr.orangemarketbackend.service.payment.AlipayNotifyVerifier;
import com.czlr.orangemarketbackend.service.payment.AlipayTradeClient;
import com.czlr.orangemarketbackend.service.payment.AlipayTradeClient.CloseOutcome;
import com.czlr.orangemarketbackend.service.payment.AlipayTradeClient.PrecreateResult;
import com.czlr.orangemarketbackend.service.payment.AlipayTradeClient.QueryResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    public static final String CHANNEL_MOCK = "mock";
    public static final String CHANNEL_ALIPAY = "alipay";
    public static final String TRADE_QR = "qr";
    public static final String TRADE_WAP = "wap";

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ALIPAY_EXPIRE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int QR_TIMEOUT_MAX_MINUTES = 120;
    private static final int SUBJECT_MAX_LENGTH = 256;

    private final OrderService orderService;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final OrderItemMapper orderItemMapper;
    private final AlipayProperties alipayProperties;
    private final AlipayTradeClient alipayTradeClient;
    private final AlipayNotifyVerifier alipayNotifyVerifier;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            OrderService orderService,
            PaymentTransactionMapper paymentTransactionMapper,
            OrderItemMapper orderItemMapper,
            AlipayProperties alipayProperties,
            AlipayTradeClient alipayTradeClient,
            AlipayNotifyVerifier alipayNotifyVerifier,
            TransactionTemplate transactionTemplate) {
        this.orderService = orderService;
        this.paymentTransactionMapper = paymentTransactionMapper;
        this.orderItemMapper = orderItemMapper;
        this.alipayProperties = alipayProperties;
        this.alipayTradeClient = alipayTradeClient;
        this.alipayNotifyVerifier = alipayNotifyVerifier;
        this.transactionTemplate = transactionTemplate;
    }

    public PayOrderResultDTO payOrder(Long userId, Long orderId, PayOrderRequest request) {
        String paymentMethod = requirePaymentMethod(request);
        Order order = orderService.getOwnedOrder(userId, orderId);
        LocalDateTime now = LocalDateTime.now();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "仅待付款订单可以支付");
        }
        if (orderService.isPaymentExpired(order, now)) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单支付已超时");
        }
        if (CHANNEL_MOCK.equals(paymentMethod)) {
            return payByMock(order, now);
        }
        String tradeType = requireTradeType(request);
        if (TRADE_WAP.equals(tradeType)) {
            return payByAlipayWap(order);
        }
        return payByAlipay(order, now);
    }

    public PayOrderResultDTO syncPayment(Long userId, Long orderId) {
        Order order = orderService.getOwnedOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            PaymentTransaction paid = findLatestSuccess(order.getId());
            return toResult(
                    order,
                    order.getPaymentMethod(),
                    order.getPaidAt(),
                    paid == null ? null : paid.getQrCode(),
                    paid == null ? null : paid.getOutTradeNo());
        }
        if (orderService.isPaymentExpired(order, LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单支付已超时");
        }
        PaymentTransaction pending = findPendingAlipay(order.getId());
        if (pending == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "尚未发起支付宝支付");
        }
        QueryResult queried = alipayTradeClient.query(pending.getOutTradeNo());
        if (queried.isPaid()) {
            settleIfPaid(pending, queried.tradeNo(), queried.buyerLogonId(), queried.totalAmount());
            Order updated = orderService.getOwnedOrder(userId, orderId);
            PaymentTransaction success = paymentTransactionMapper.selectById(pending.getId());
            return toResult(
                    updated,
                    CHANNEL_ALIPAY,
                    updated.getPaidAt(),
                    success == null ? pending.getQrCode() : success.getQrCode(),
                    pending.getOutTradeNo());
        }
        String payUrl = null;
        if (isBlank(pending.getQrCode())) {
            payUrl = alipayTradeClient.wapPay(
                    pending.getOutTradeNo(),
                    scaleAmount(order.getTotalAmount()).toPlainString(),
                    buildSubject(order.getId()),
                    formatExpire(order.getPaymentExpireAt()),
                    alipayNotifyUrl(),
                    alipayProperties.returnUrlFor(order.getId()));
        }
        return toResult(order, CHANNEL_ALIPAY, null, pending.getQrCode(), pending.getOutTradeNo(), payUrl);
    }

    public String handleAlipayNotify(HttpServletRequest request) {
        return handleAlipayNotify(extractNotifyParams(request));
    }

    public String handleAlipayNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "fail";
        }
        if (!alipayNotifyVerifier.verify(params, alipayProperties.getAlipayPublicKey())) {
            log.warn("支付宝异步通知验签失败 out_trade_no={}", params.get("out_trade_no"));
            return "fail";
        }
        String appId = params.get("app_id");
        if (!alipayProperties.getAppId().equals(appId)) {
            log.warn("支付宝异步通知 app_id 不匹配");
            return "fail";
        }
        String outTradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        if (isBlank(outTradeNo)) {
            return "fail";
        }
        if (!AlipayTradeClient.TRADE_SUCCESS.equals(tradeStatus)
                && !AlipayTradeClient.TRADE_FINISHED.equals(tradeStatus)) {
            return "success";
        }
        PaymentTransaction transaction = findByOutTradeNo(outTradeNo);
        if (transaction == null) {
            log.warn("支付宝异步通知找不到流水 out_trade_no={}", outTradeNo);
            return "fail";
        }
        if (!amountMatches(transaction.getAmount(), params.get("total_amount"))) {
            log.warn("支付宝异步通知金额不一致 out_trade_no={}", outTradeNo);
            return "success";
        }
        settleIfPaid(transaction, params.get("trade_no"), firstNonBlank(
                params.get("buyer_logon_id"), params.get("buyer_id")), params.get("total_amount"));
        return "success";
    }

    public void cancelOrder(Long userId, Long orderId, CancelOrderRequest request) {
        Order order = orderService.getOwnedOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "仅待付款订单可以取消");
        }
        closeOrCapturePendingAlipay(order, true);
        orderService.cancelOrder(userId, orderId, request);
    }

    public void handlePaymentTimeout(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return;
        }
        Order order = orderService.getById(orderId);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        closeOrCapturePendingAlipay(order, false);
        orderService.cancelExpiredOrder(orderId);
    }

    private PayOrderResultDTO payByMock(Order order, LocalDateTime paidAt) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(order.getId());
        transaction.setOutTradeNo(nextOutTradeNo(order.getId()));
        transaction.setChannel(CHANNEL_MOCK);
        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setAmount(scaleAmount(order.getTotalAmount()));
        transaction.setPaidAt(paidAt);
        paymentTransactionMapper.insert(transaction);
        if (!orderService.markPaidIfPending(order.getId(), CHANNEL_MOCK, paidAt)) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单状态已发生变化或支付已超时");
        }
        order.setStatus(OrderStatus.PENDING_SHIPMENT);
        order.setPaymentMethod(CHANNEL_MOCK);
        order.setPaidAt(paidAt);
        return toResult(order, CHANNEL_MOCK, paidAt, null, transaction.getOutTradeNo());
    }

    private PayOrderResultDTO payByAlipayWap(Order order) {
        if (!alipayProperties.isConfigured()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "支付宝未配置");
        }
        PaymentTransaction pending = findPendingAlipay(order.getId());
        if (pending != null && !isBlank(pending.getQrCode())) {
            retirePending(pending);
            pending = null;
        }
        boolean created = pending == null;
        String outTradeNo;
        if (pending == null) {
            outTradeNo = nextOutTradeNo(order.getId());
            pending = new PaymentTransaction();
            pending.setOrderId(order.getId());
            pending.setOutTradeNo(outTradeNo);
            pending.setChannel(CHANNEL_ALIPAY);
            pending.setStatus(PaymentStatus.PENDING);
            pending.setAmount(scaleAmount(order.getTotalAmount()));
            paymentTransactionMapper.insert(pending);
        } else {
            outTradeNo = pending.getOutTradeNo();
        }
        try {
            String payUrl = alipayTradeClient.wapPay(
                    outTradeNo,
                    scaleAmount(order.getTotalAmount()).toPlainString(),
                    buildSubject(order.getId()),
                    formatExpire(order.getPaymentExpireAt()),
                    alipayNotifyUrl(),
                    alipayProperties.returnUrlFor(order.getId()));
            return toResult(order, CHANNEL_ALIPAY, null, null, outTradeNo, payUrl);
        } catch (RuntimeException e) {
            if (created) {
                pending.setStatus(PaymentStatus.FAILED);
                paymentTransactionMapper.updateById(pending);
            }
            throw e;
        }
    }

    private PayOrderResultDTO payByAlipay(Order order, LocalDateTime now) {
        if (!alipayProperties.isConfigured()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "支付宝未配置");
        }
        PaymentTransaction pending = findPendingAlipay(order.getId());
        if (pending != null && !isBlank(pending.getQrCode())) {
            return toResult(order, CHANNEL_ALIPAY, null, pending.getQrCode(), pending.getOutTradeNo());
        }
        if (pending != null) {
            retirePending(pending);
        }

        String outTradeNo = nextOutTradeNo(order.getId());
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(order.getId());
        transaction.setOutTradeNo(outTradeNo);
        transaction.setChannel(CHANNEL_ALIPAY);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setAmount(scaleAmount(order.getTotalAmount()));
        paymentTransactionMapper.insert(transaction);

        String notifyUrl = alipayNotifyUrl();
        try {
            PrecreateResult result = alipayTradeClient.precreate(
                    outTradeNo,
                    scaleAmount(order.getTotalAmount()).toPlainString(),
                    buildSubject(order.getId()),
                    formatExpire(order.getPaymentExpireAt()),
                    qrTimeoutExpress(order.getPaymentExpireAt(), now),
                    notifyUrl);
            transaction.setQrCode(result.qrCode());
            paymentTransactionMapper.updateById(transaction);
            return toResult(order, CHANNEL_ALIPAY, null, result.qrCode(), outTradeNo);
        } catch (RuntimeException e) {
            transaction.setStatus(PaymentStatus.FAILED);
            paymentTransactionMapper.updateById(transaction);
            throw e;
        }
    }

    public boolean settleIfPaid(
            PaymentTransaction transaction,
            String tradeNo,
            String buyerLogonId,
            String totalAmount) {
        if (transaction == null) {
            return false;
        }
        if (!amountMatches(transaction.getAmount(), totalAmount) && !isBlank(totalAmount)) {
            log.warn("支付金额不一致，拒绝落库 out_trade_no={}", transaction.getOutTradeNo());
            return false;
        }
        Boolean settled = transactionTemplate.execute(status -> persistPaid(transaction, tradeNo, buyerLogonId));
        return Boolean.TRUE.equals(settled);
    }

    private boolean persistPaid(PaymentTransaction transaction, String tradeNo, String buyerLogonId) {
        LocalDateTime paidAt = LocalDateTime.now();
        PaymentTransaction latest = paymentTransactionMapper.selectById(transaction.getId());
        if (latest == null) {
            latest = transaction;
        }
        if (latest.getStatus() != PaymentStatus.SUCCESS) {
            latest.setStatus(PaymentStatus.SUCCESS);
            latest.setTradeNo(tradeNo);
            latest.setBuyerLogonId(buyerLogonId);
            latest.setPaidAt(paidAt);
            paymentTransactionMapper.updateById(latest);
        } else if (!isBlank(tradeNo) && isBlank(latest.getTradeNo())) {
            latest.setTradeNo(tradeNo);
            latest.setBuyerLogonId(buyerLogonId);
            paymentTransactionMapper.updateById(latest);
        }
        boolean orderPaid = orderService.markPaidIfPending(latest.getOrderId(), CHANNEL_ALIPAY, paidAt);
        if (!orderPaid) {
            log.warn("支付宝已支付但订单无法标记成功 orderId={} out_trade_no={}",
                    latest.getOrderId(), latest.getOutTradeNo());
        }
        return true;
    }

    private void closeOrCapturePendingAlipay(Order order, boolean failIfAlreadyPaid) {
        PaymentTransaction pending = findPendingAlipay(order.getId());
        if (pending == null) {
            return;
        }
        QueryResult queried = alipayTradeClient.query(pending.getOutTradeNo());
        if (queried.isPaid()) {
            settleIfPaid(pending, queried.tradeNo(), queried.buyerLogonId(), queried.totalAmount());
            if (failIfAlreadyPaid) {
                throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单已支付，无法取消");
            }
            return;
        }
        if (!queried.notFound() && !queried.isClosed()) {
            CloseOutcome closeOutcome = alipayTradeClient.close(pending.getOutTradeNo());
            if (closeOutcome == CloseOutcome.ALREADY_PAID) {
                QueryResult paid = alipayTradeClient.query(pending.getOutTradeNo());
                settleIfPaid(
                        pending,
                        firstNonBlank(paid.tradeNo(), queried.tradeNo()),
                        firstNonBlank(paid.buyerLogonId(), queried.buyerLogonId()),
                        firstNonBlank(paid.totalAmount(), queried.totalAmount()));
                if (failIfAlreadyPaid) {
                    throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单已支付，无法取消");
                }
                return;
            }
        }
        pending.setStatus(PaymentStatus.CANCELLED);
        paymentTransactionMapper.updateById(pending);
    }

    private String requirePaymentMethod(PayOrderRequest request) {
        String paymentMethod = request == null ? null : request.getPaymentMethod();
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "paymentMethod 不能为空");
        }
        paymentMethod = paymentMethod.trim();
        if (CHANNEL_MOCK.equals(paymentMethod)) {
            if (!alipayProperties.isAllowMock()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "当前不支持 mock 支付");
            }
            return paymentMethod;
        }
        if (CHANNEL_ALIPAY.equals(paymentMethod)) {
            return paymentMethod;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "当前仅支持 alipay 或 mock 支付");
    }

    private String requireTradeType(PayOrderRequest request) {
        String tradeType = request == null ? null : request.getTradeType();
        if (tradeType == null || tradeType.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择当面扫码或手机网站支付");
        }
        tradeType = tradeType.trim().toLowerCase(Locale.ROOT);
        if (TRADE_QR.equals(tradeType) || TRADE_WAP.equals(tradeType)) {
            return tradeType;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "tradeType 仅支持 qr 或 wap");
    }

    private String alipayNotifyUrl() {
        return alipayProperties.hasUsableNotifyUrl() ? alipayProperties.getNotifyUrl() : null;
    }

    private void retirePending(PaymentTransaction pending) {
        QueryResult queried = alipayTradeClient.query(pending.getOutTradeNo());
        if (queried.isPaid()) {
            settleIfPaid(pending, queried.tradeNo(), queried.buyerLogonId(), queried.totalAmount());
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单已支付");
        }
        if (!queried.notFound() && !queried.isClosed()) {
            CloseOutcome closeOutcome = alipayTradeClient.close(pending.getOutTradeNo());
            if (closeOutcome == CloseOutcome.ALREADY_PAID) {
                QueryResult paid = alipayTradeClient.query(pending.getOutTradeNo());
                settleIfPaid(
                        pending,
                        firstNonBlank(paid.tradeNo(), queried.tradeNo()),
                        firstNonBlank(paid.buyerLogonId(), queried.buyerLogonId()),
                        firstNonBlank(paid.totalAmount(), queried.totalAmount()));
                throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单已支付");
            }
        }
        pending.setStatus(PaymentStatus.FAILED);
        paymentTransactionMapper.updateById(pending);
    }

    private PaymentTransaction findPendingAlipay(Long orderId) {
        return paymentTransactionMapper.selectOne(new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getOrderId, orderId)
                .eq(PaymentTransaction::getChannel, CHANNEL_ALIPAY)
                .eq(PaymentTransaction::getStatus, PaymentStatus.PENDING)
                .orderByDesc(PaymentTransaction::getId)
                .last("LIMIT 1"));
    }

    private PaymentTransaction findLatestSuccess(Long orderId) {
        return paymentTransactionMapper.selectOne(new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getOrderId, orderId)
                .eq(PaymentTransaction::getStatus, PaymentStatus.SUCCESS)
                .orderByDesc(PaymentTransaction::getId)
                .last("LIMIT 1"));
    }

    private PaymentTransaction findByOutTradeNo(String outTradeNo) {
        return paymentTransactionMapper.selectOne(new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getOutTradeNo, outTradeNo));
    }

    private String buildSubject(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        String subject;
        if (items.isEmpty()) {
            subject = "橙子市集订单";
        } else if (items.size() == 1) {
            subject = items.getFirst().getProductName();
        } else {
            subject = items.getFirst().getProductName() + "等" + items.size() + "件";
        }
        if (subject == null || subject.isBlank()) {
            subject = "橙子市集订单";
        }
        return subject.length() > SUBJECT_MAX_LENGTH ? subject.substring(0, SUBJECT_MAX_LENGTH) : subject;
    }

    private String formatExpire(LocalDateTime expireAt) {
        if (expireAt == null) {
            return null;
        }
        return expireAt.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(SHANGHAI)
                .format(ALIPAY_EXPIRE_FORMATTER);
    }

    private String qrTimeoutExpress(LocalDateTime expireAt, LocalDateTime now) {
        long minutes = 30;
        if (expireAt != null) {
            minutes = Duration.between(now, expireAt).toMinutes();
        }
        if (minutes < 1) {
            minutes = 1;
        }
        if (minutes > QR_TIMEOUT_MAX_MINUTES) {
            minutes = QR_TIMEOUT_MAX_MINUTES;
        }
        return minutes + "m";
    }

    private String nextOutTradeNo(Long orderId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "P" + orderId + "T" + System.currentTimeMillis() + suffix;
    }

    private PayOrderResultDTO toResult(
            Order order,
            String paymentMethod,
            LocalDateTime paidAt,
            String qrCode,
            String outTradeNo) {
        return toResult(order, paymentMethod, paidAt, qrCode, outTradeNo, null);
    }

    private PayOrderResultDTO toResult(
            Order order,
            String paymentMethod,
            LocalDateTime paidAt,
            String qrCode,
            String outTradeNo,
            String payUrl) {
        return new PayOrderResultDTO(
                String.valueOf(order.getId()),
                order.getOrderNo(),
                order.getStatus(),
                paymentMethod,
                paidAt,
                qrCode,
                outTradeNo,
                order.getPaymentExpireAt(),
                payUrl);
    }

    static Map<String, String> extractNotifyParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String[] values = request.getParameterValues(name);
            if (values == null || values.length == 0) {
                continue;
            }
            params.put(name, String.join(",", values));
        }
        return params;
    }

    static boolean amountMatches(BigDecimal expected, String actual) {
        if (expected == null || isBlank(actual)) {
            return false;
        }
        try {
            return scaleAmount(expected).compareTo(scaleAmount(new BigDecimal(actual.trim()))) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static BigDecimal scaleAmount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
