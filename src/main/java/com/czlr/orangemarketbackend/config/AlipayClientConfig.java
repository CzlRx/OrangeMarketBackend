package com.czlr.orangemarketbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 支付宝客户端由 {@link com.czlr.orangemarketbackend.service.payment.AlipayTradeClient}
 * 在密钥配置齐全后惰性创建，避免本地未配密钥时阻断启动。
 */
@Configuration
public class AlipayClientConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
