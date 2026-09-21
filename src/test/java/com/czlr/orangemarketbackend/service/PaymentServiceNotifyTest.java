package com.czlr.orangemarketbackend.service;

import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import com.czlr.orangemarketbackend.common.enums.PaymentStatus;
import com.czlr.orangemarketbackend.config.AlipayProperties;
import com.czlr.orangemarketbackend.entity.po.Order;
import com.czlr.orangemarketbackend.entity.po.PaymentTransaction;
import com.czlr.orangemarketbackend.mapper.OrderItemMapper;
import com.czlr.orangemarketbackend.mapper.PaymentTransactionMapper;
import com.czlr.orangemarketbackend.service.payment.AlipayNotifyVerifier;
import com.czlr.orangemarketbackend.service.payment.AlipayTradeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceNotifyTest {

    private OrderService orderService;
    private PaymentTransactionMapper paymentTransactionMapper;
    private AlipayProperties alipayProperties;
    private AlipayNotifyVerifier verifier;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        paymentTransactionMapper = mock(PaymentTransactionMapper.class);
        alipayProperties = mock(AlipayProperties.class);
        verifier = mock(AlipayNotifyVerifier.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(alipayProperties.getAppId()).thenReturn("2021007100677819");
        when(alipayProperties.getAlipayPublicKey()).thenReturn("test-public-key");
        paymentService = new PaymentService(
                orderService,
                paymentTransactionMapper,
                mock(OrderItemMapper.class),
                alipayProperties,
                mock(AlipayTradeClient.class),
                verifier,
                transactionTemplate);
    }

    @Test
    void rejectsInvalidSignature() {
        Map<String, String> params = paidNotify("10.00");
        when(verifier.verify(eq(params), anyString())).thenReturn(false);

        assertEquals("fail", paymentService.handleAlipayNotify(params));
        verify(orderService, never()).markPaidIfPending(any(), any(), any());
    }

    @Test
    void ignoresAmountMismatchWithoutMarkingPaid() {
        Map<String, String> params = paidNotify("9.99");
        when(verifier.verify(eq(params), anyString())).thenReturn(true);
        when(paymentTransactionMapper.selectOne(any())).thenReturn(pendingTransaction());

        assertEquals("success", paymentService.handleAlipayNotify(params));
        verify(orderService, never()).markPaidIfPending(any(), any(), any());
        verify(paymentTransactionMapper, never()).update(any(), any());
    }

    @Test
    void settlesPaidNotify() {
        Map<String, String> params = paidNotify("10.00");
        when(verifier.verify(eq(params), anyString())).thenReturn(true);
        PaymentTransaction transaction = pendingTransaction();
        when(paymentTransactionMapper.selectOne(any())).thenReturn(transaction);
        when(paymentTransactionMapper.updateById(any(PaymentTransaction.class))).thenReturn(1);
        when(orderService.markPaidIfPending(eq(60001L), eq("alipay"), any(LocalDateTime.class)))
                .thenReturn(true);

        assertEquals("success", paymentService.handleAlipayNotify(params));
        verify(paymentTransactionMapper).updateById(any(PaymentTransaction.class));
        verify(orderService).markPaidIfPending(eq(60001L), eq("alipay"), any(LocalDateTime.class));
    }

    @Test
    void secondNotifyIsIdempotent() {
        Map<String, String> params = paidNotify("10.00");
        when(verifier.verify(eq(params), anyString())).thenReturn(true);
        PaymentTransaction transaction = pendingTransaction();
        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setTradeNo("202609212200110001");
        transaction.setPaidAt(LocalDateTime.now());
        when(paymentTransactionMapper.selectOne(any())).thenReturn(transaction);
        when(orderService.markPaidIfPending(eq(60001L), eq("alipay"), any(LocalDateTime.class)))
                .thenReturn(false);

        assertEquals("success", paymentService.handleAlipayNotify(params));
        verify(orderService).markPaidIfPending(eq(60001L), eq("alipay"), any(LocalDateTime.class));
    }

    private static Map<String, String> paidNotify(String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", "2021007100677819");
        params.put("out_trade_no", "P60001T1");
        params.put("trade_no", "202609212200110001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", amount);
        params.put("sign", "ok");
        params.put("sign_type", "RSA2");
        return params;
    }

    private static PaymentTransaction pendingTransaction() {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(1L);
        transaction.setOrderId(60001L);
        transaction.setOutTradeNo("P60001T1");
        transaction.setChannel("alipay");
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setAmount(new BigDecimal("10.00"));
        Order order = new Order();
        order.setId(60001L);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        return transaction;
    }
}
