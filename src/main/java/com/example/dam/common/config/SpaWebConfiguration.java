package com.example.dam.common.config;

import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled web UI (built into {@code classpath:/static}, see the {@code frontend} Maven profile) as a
 * single-page application: unknown client-side routes such as {@code /assets/123} return {@code index.html}, while
 * API, actuator, documentation and missing static files keep returning 404.
 */
@Configuration(proxyBeanMethods = false)
class SpaWebConfiguration implements WebMvcConfigurer {

    private static final List<String> SERVER_PREFIXES = List.of("api/", "actuator", "v3/", "swagger-ui", "mock-dam/");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    private static final class SpaResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            boolean serverPath = SERVER_PREFIXES.stream().anyMatch(resourcePath::startsWith);
            boolean file = resourcePath.substring(resourcePath.lastIndexOf('/') + 1).contains(".");
            if (serverPath || file) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }
    }
}
