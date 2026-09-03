package com.czlr.orangemarketbackend;

import com.czlr.orangemarketbackend.utils.JwtUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GenerateTestToken {

    public static void main(String[] args) {
        SpringApplication.run(GenerateTestToken.class, args);
    }

    @Bean
    CommandLineRunner run(JwtUtil jwtUtil) {
        return args -> {
            String token = jwtUtil.generateToken("test-session-123", 1L, "13800138000");
            System.out.println("\n=== TEST TOKEN ===");
            System.out.println(token);
            System.out.println("==================\n");
            System.exit(0);
        };
    }
}
