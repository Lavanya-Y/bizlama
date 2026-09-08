package com.bizlama.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BizlaMaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BizlaMaApplication.class, args);
    }
}