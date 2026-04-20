package com.tech.docflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .components(new Components().addSecuritySchemes(BEARER_SCHEME,
            new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")))
                .info(new Info()
                        .title("Document Management System API")
                        .version("1.0.0")
                        .description("Enterprise-grade document management with OCR, workflows, and audit trails"));
    }

}