package com.czlr.orangemarketbackend;

import org.apache.shiro.spring.boot.autoconfigure.ShiroAutoConfiguration;
import org.apache.shiro.spring.config.web.autoconfigure.ShiroWebAutoConfiguration;
import org.apache.shiro.spring.config.web.autoconfigure.ShiroWebFilterConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
    ShiroAutoConfiguration.class,
    ShiroWebAutoConfiguration.class,
    ShiroWebFilterConfiguration.class
})
public class OrangeMarketBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrangeMarketBackendApplication.class, args);
    }

}
