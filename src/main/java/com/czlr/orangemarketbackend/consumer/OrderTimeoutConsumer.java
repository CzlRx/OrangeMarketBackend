package com.czlr.orangemarketbackend.consumer;

import com.czlr.orangemarketbackend.config.RabbitConfig;
import com.czlr.orangemarketbackend.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    public OrderTimeoutConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_CANCEL_QUEUE)
    public void consume(Long orderId) {
        orderService.cancelExpiredOrder(orderId);
    }


}
