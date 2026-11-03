package com.lapzone.lapzoneweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EntityScan("com.lapzone.lapzoneweb.model.entity")
@EnableCaching
public class LapzonewebApplication {

    public static void main(String[] args) {
        SpringApplication.run(LapzonewebApplication.class, args);
        System.out.println("--- LAPZONE SERVER IS RUNNING ---");
    }

}