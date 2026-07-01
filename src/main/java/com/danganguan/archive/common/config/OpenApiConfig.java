package com.danganguan.archive.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "在线档案馆 API",
                version = "0.0.1",
                description = "在线档案馆 MVP 后端接口文档"
        )
)
public class OpenApiConfig {
}
