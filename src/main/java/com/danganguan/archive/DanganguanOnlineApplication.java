package com.danganguan.archive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@MapperScan("com.danganguan.archive.**.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication
public class DanganguanOnlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(DanganguanOnlineApplication.class, args);
    }
}
