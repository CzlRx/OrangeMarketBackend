package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/payments/alipay")
public class AlipayNotifyController {

    private final PaymentService paymentService;

    public AlipayNotifyController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(HttpServletRequest request) {
        try {
            String result = paymentService.handleAlipayNotify(request);
            return result == null ? "fail" : result;
        } catch (Exception e) {
            log.warn("处理支付宝异步通知失败", e);
            return "fail";
        }
    }
}
