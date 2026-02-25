package com.snapplayerapi.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SnapPlayerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnapPlayerApiApplication.class, args);
    }
}
