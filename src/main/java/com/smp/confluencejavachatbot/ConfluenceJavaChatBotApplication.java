package com.smp.confluencejavachatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConfluenceJavaChatBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfluenceJavaChatBotApplication.class, args);
    }

}
