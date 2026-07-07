package com.danganguan.archive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@MapperScan("com.danganguan.archive.**.mapper")
@ConfigurationPropertiesScan
@EnableAsync
@SpringBootApplication
public class DanganguanOnlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(DanganguanOnlineApplication.class, args);
    }
}
