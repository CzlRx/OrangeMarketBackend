package com.czlr.orangemarketbackend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String PAYMENT_TIMEOUT_EXCHANGE = "order.payment.timeout.exchange";
    public static final String PAYMENT_TIMEOUT_QUEUE = "order.payment.timeout.queue";
    public static final String PAYMENT_TIMEOUT_ROUTING_KEY = "order.payment.timeout";
    public static final int PAYMENT_TIMEOUT_MILLIS = 30 * 60 * 1000;
    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    @Bean
    public DirectExchange paymentTimeoutExchange() {
        return new DirectExchange(PAYMENT_TIMEOUT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentTimeoutQueue() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_QUEUE)
                .ttl(PAYMENT_TIMEOUT_MILLIS)
                .deadLetterExchange(ORDER_CANCEL_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CANCEL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding paymentTimeoutBinding(
            @Qualifier("paymentTimeoutQueue") Queue paymentTimeoutQueue,
            @Qualifier("paymentTimeoutExchange") DirectExchange paymentTimeoutExchange) {
        return BindingBuilder.bind(paymentTimeoutQueue)
                .to(paymentTimeoutExchange)
                .with(PAYMENT_TIMEOUT_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelBinding(
            @Qualifier("orderCancelQueue") Queue orderCancelQueue,
            @Qualifier("orderCancelExchange") DirectExchange orderCancelExchange) {
        return BindingBuilder.bind(orderCancelQueue)
                .to(orderCancelExchange)
                .with(ORDER_CANCEL_ROUTING_KEY);
    }
}
