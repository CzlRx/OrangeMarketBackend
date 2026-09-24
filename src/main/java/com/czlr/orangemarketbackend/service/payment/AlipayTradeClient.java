package com.czlr.orangemarketbackend.service.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AlipayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AlipayTradeClient {

    private static final int GATEWAY_ATTEMPTS = 3;
    private static final long GATEWAY_RETRY_DELAY_MS = 300;

    public static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    public static final String TRADE_FINISHED = "TRADE_FINISHED";
    public static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";
    public static final String FACE_TO_FACE_PRODUCT_CODE = "FACE_TO_FACE_PAYMENT";
    public static final String PAGE_PAY_PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";
    public static final String WAP_PAY_PRODUCT_CODE = "QUICK_WAP_WAY";

    private final AlipayProperties properties;
    private final Object clientLock = new Object();
    private volatile AlipayClient client;

    public AlipayTradeClient(AlipayProperties properties) {
        this.properties = properties;
    }

    public PrecreateResult precreate(
            String outTradeNo,
            String totalAmount,
            String subject,
            String timeExpire,
            String qrCodeTimeoutExpress,
            String notifyUrl) {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            request.setNotifyUrl(notifyUrl);
        }
        request.setNeedEncrypt(true);
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(outTradeNo);
        model.setTotalAmount(totalAmount);
        model.setSubject(subject);
        model.setProductCode(FACE_TO_FACE_PRODUCT_CODE);
        if (timeExpire != null && !timeExpire.isBlank()) {
            model.setTimeExpire(timeExpire);
        }
        if (qrCodeTimeoutExpress != null && !qrCodeTimeoutExpress.isBlank()) {
            model.setQrCodeTimeoutExpress(qrCodeTimeoutExpress);
        }
        request.setBizModel(model);
        AlipayTradePrecreateResponse response = execute("支付宝预下单失败", () -> client().execute(request));
        if (response == null || !response.isSuccess() || isBlank(response.getQrCode())) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, precreateError(response));
        }
        return new PrecreateResult(response.getOutTradeNo(), response.getQrCode());
    }

    public String pagePay(
            String outTradeNo,
            String totalAmount,
            String subject,
            String timeExpire,
            String notifyUrl,
            String returnUrl) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            request.setNotifyUrl(notifyUrl);
        }
        if (returnUrl != null && !returnUrl.isBlank()) {
            request.setReturnUrl(returnUrl);
        }
        request.setNeedEncrypt(true);
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(outTradeNo);
        model.setTotalAmount(totalAmount);
        model.setSubject(subject);
        model.setProductCode(PAGE_PAY_PRODUCT_CODE);
        if (timeExpire != null && !timeExpire.isBlank()) {
            model.setTimeExpire(timeExpire);
        }
        request.setBizModel(model);
        AlipayTradePagePayResponse response;
        try {
            response = client().pageExecute(request, "GET");
        } catch (AlipayApiException e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "支付宝收银台下单失败");
        }
        String payUrl = response == null ? null : response.getBody();
        if (isBlank(payUrl) || !payUrl.startsWith("http")) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "支付宝收银台下单失败");
        }
        return payUrl;
    }

    public String wapPay(
            String outTradeNo,
            String totalAmount,
            String subject,
            String timeExpire,
            String notifyUrl,
            String returnUrl) {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            request.setNotifyUrl(notifyUrl);
        }
        if (returnUrl != null && !returnUrl.isBlank()) {
            request.setReturnUrl(returnUrl);
        }
        request.setNeedEncrypt(true);
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(outTradeNo);
        model.setTotalAmount(totalAmount);
        model.setSubject(subject);
        model.setProductCode(WAP_PAY_PRODUCT_CODE);
        if (timeExpire != null && !timeExpire.isBlank()) {
            model.setTimeExpire(timeExpire);
        }
        request.setBizModel(model);
        AlipayTradeWapPayResponse response;
        try {
            response = client().pageExecute(request, "GET");
        } catch (AlipayApiException e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "支付宝手机网站支付下单失败");
        }
        String payUrl = response == null ? null : response.getBody();
        if (isBlank(payUrl) || !payUrl.startsWith("http")) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "支付宝手机网站支付下单失败");
        }
        return payUrl;
    }

    public QueryResult query(String outTradeNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setNeedEncrypt(true);
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);
        AlipayTradeQueryResponse response = execute("支付宝查单失败", () -> client().execute(request));
        if (response == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "支付宝查单失败");
        }
        if (!response.isSuccess()) {
            if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                return QueryResult.notFound(outTradeNo);
            }
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, queryError(response));
        }
        return new QueryResult(
                outTradeNo,
                response.getTradeNo(),
                response.getTradeStatus(),
                response.getTotalAmount(),
                firstNonBlank(response.getBuyerLogonId(), response.getBuyerUserId()));
    }

    public CloseOutcome close(String outTradeNo) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setNeedEncrypt(true);
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);
        AlipayTradeCloseResponse response = execute("支付宝关单失败", () -> client().execute(request));
        if (response != null && (response.isSuccess() || TRADE_NOT_EXIST.equals(response.getSubCode()))) {
            return CloseOutcome.CLOSED;
        }
        QueryResult queried = query(outTradeNo);
        if (queried.isPaid()) {
            return CloseOutcome.ALREADY_PAID;
        }
        if (queried.notFound() || queried.isClosed()) {
            return CloseOutcome.CLOSED;
        }
        throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, closeError(response));
    }

    private <T> T execute(String failureMessage, AlipayCall<T> call) {
        AlipayApiException last = null;
        for (int attempt = 1; attempt <= GATEWAY_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (AlipayApiException e) {
                last = e;
                if (attempt == GATEWAY_ATTEMPTS) {
                    break;
                }
                log.warn("支付宝网关通信失败，准备重试 attempt={} message={}", attempt, e.getMessage());
                if (!pauseBeforeRetry()) {
                    break;
                }
            }
        }
        log.warn("支付宝网关通信失败 message={}", last == null ? null : last.getErrMsg(), last);
        throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, failureMessage);
    }

    private static boolean pauseBeforeRetry() {
        try {
            Thread.sleep(GATEWAY_RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private AlipayClient client() {
        if (!properties.isConfigured()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "支付宝未配置");
        }
        AlipayClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (clientLock) {
            if (client == null) {
                try {
                    client = new DefaultAlipayClient(properties.toSdkConfig());
                } catch (AlipayApiException e) {
                    throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "支付宝客户端初始化失败");
                }
            }
            return client;
        }
    }

    private static String precreateError(AlipayTradePrecreateResponse response) {
        if (response == null) {
            return "支付宝预下单失败";
        }
        return firstNonBlank(response.getSubMsg(), response.getMsg(), "支付宝预下单失败");
    }

    private static String queryError(AlipayTradeQueryResponse response) {
        return firstNonBlank(response.getSubMsg(), response.getMsg(), "支付宝查单失败");
    }

    private static String closeError(AlipayTradeCloseResponse response) {
        if (response == null) {
            return "支付宝关单失败";
        }
        return firstNonBlank(response.getSubMsg(), response.getMsg(), "支付宝关单失败");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface AlipayCall<T> {
        T get() throws AlipayApiException;
    }

    public enum CloseOutcome {
        CLOSED,
        ALREADY_PAID
    }

    public record PrecreateResult(String outTradeNo, String qrCode) {
    }

    public record QueryResult(
            String outTradeNo,
            String tradeNo,
            String tradeStatus,
            String totalAmount,
            String buyerLogonId) {

        public static QueryResult notFound(String outTradeNo) {
            return new QueryResult(outTradeNo, null, "NOT_EXIST", null, null);
        }

        public boolean isPaid() {
            return TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus);
        }

        public boolean notFound() {
            return "NOT_EXIST".equals(tradeStatus);
        }

        public boolean isClosed() {
            return "TRADE_CLOSED".equals(tradeStatus);
        }
    }
}
