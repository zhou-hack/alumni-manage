package com.alumni.archives;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.alumni.archives", "com.alumni.common"})
public class ArchivesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchivesApplication.class, args);
    }
}
