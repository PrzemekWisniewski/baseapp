package com.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@SuppressWarnings({"squid:S2095", "squid:S1118"})
public class BaseApp {

    public static void main(String[] args) {
        SpringApplication.run(BaseApp.class, args);
    }
}
