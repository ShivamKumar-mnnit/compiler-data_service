package com.compiler.dataservice;

import com.compiler.dataservice.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CompilerDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompilerDataServiceApplication.class, args);
    }
}
