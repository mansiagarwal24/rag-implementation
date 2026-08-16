package com.testcase.rag_implement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ragOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Document Q&A Assistant (RAG)")
                .version("v1")
                .description("""
                        Retrieval-Augmented Generation backend for school policy documents.
                        All endpoints require the `X-Tenant-Id` header. An optional
                        `X-Correlation-Id` header is echoed back for request tracing.
                        """)
                .license(new License().name("Proprietary")));
    }
}
