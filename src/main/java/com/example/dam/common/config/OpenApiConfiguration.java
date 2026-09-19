package com.example.dam.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI damOpenApi() {
        // Relative server URL so "Try it out" targets the origin Swagger UI was loaded from (e.g. the Vite proxy)
        return new OpenAPI().servers(List.of(new Server().url("/"))).info(new Info()
                .title("DAM Metadata Management API")
                .version("v1")
                .description("""
                        Digital asset metadata management with deterministic validation, governed AI enrichment \
                        (LangChain4j Agentic), human approval and auditable changes. Binary assets are external \
                        resources referenced by URL / external asset id.

                        Write and approval endpoints identify the caller through the `X-User-Id` header \
                        (stand-in for a real identity provider).""")
                .license(new License().name("Proof of concept")));
    }
}
