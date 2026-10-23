package com.example.chatapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger / OpenAPI metadata. UI: /swagger-ui.html, raw spec: /v3/api-docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chatOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Redis Chat Application API")
                .version("1.0.0")
                .description("Chat rooms, messaging and real-time updates backed by Redis "
                        + "(Hash, Set, List, Pub/Sub)."));
    }
}
