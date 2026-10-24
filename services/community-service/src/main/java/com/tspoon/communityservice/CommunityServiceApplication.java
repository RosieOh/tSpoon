package com.tspoon.communityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class CommunityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityServiceApplication.class, args);
    }

}
