package com.oddplayerapi.mvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OddPlayerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OddPlayerApiApplication.class, args);
    }
}
