package com.ritech.exports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RitechApplication {

    public static void main(String[] args) {
        SpringApplication.run(RitechApplication.class, args);
    }
}
