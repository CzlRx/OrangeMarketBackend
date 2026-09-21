package com.czlr.orangemarketbackend.consumer;

import com.czlr.orangemarketbackend.config.RabbitConfig;
import com.czlr.orangemarketbackend.service.PaymentService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutConsumer {

    private final PaymentService paymentService;

    public OrderTimeoutConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_CANCEL_QUEUE)
    public void consume(Long orderId) {
        paymentService.handlePaymentTimeout(orderId);
    }
}
