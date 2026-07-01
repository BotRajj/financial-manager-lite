package com.financialmanager.lite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinancialManagerLiteApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinancialManagerLiteApplication.class, args);
    }
}
